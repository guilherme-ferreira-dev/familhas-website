package br.com.familhas.admin.quote;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.admin.PricedItemResponse;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.quote.ManualQuoteTerms;
import br.com.familhas.quote.QuoteEntity;
import br.com.familhas.quote.QuoteService;
import br.com.familhas.quote.QuoteStatus;

/** Cotações no Admin (ADR 0011): todas as cotações e a cotação manual para um cliente. */
@Service
public class AdminQuoteService {

    static final Set<String> SORTABLE = Set.of("createdAt", "expiresAt", "totalAmount");

    private final QuoteService quoteService;
    private final AdminQuoteRepository quoteRepository;
    private final ClientRepository clientRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public AdminQuoteService(QuoteService quoteService, AdminQuoteRepository quoteRepository,
            ClientRepository clientRepository, BookingRepository bookingRepository, Clock clock) {
        this.quoteService = quoteService;
        this.quoteRepository = quoteRepository;
        this.clientRepository = clientRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminQuoteDetailResponse create(ManualQuoteRequest request, AuditActor actor) {
        ClientEntity client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found: " + request.clientId()));
        if (client.getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CLIENT_INACTIVE",
                    "Cliente inativo nao pode receber cotacao.");
        }
        UUID id = quoteService.createManual(client.getId(), request.toQuoteRequest(), new ManualQuoteTerms(
                request.manualDiscount(),
                request.validityHours() == null ? null : Duration.ofHours(request.validityHours()),
                request.internalNote(),
                actor.displayName()));
        return detail(quoteRepository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminQuoteSummaryResponse> findAll(AdminQuoteFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        Instant now = clock.instant();
        return PageResponse.of(quoteRepository.findAll(toSpecification(filter, now), pageable),
                q -> new AdminQuoteSummaryResponse(q.getId(), statusAt(q, now), q.getOrigin(),
                        ClientRef.of(q.getClient()), q.getTravelPackage() == null ? null : q.getTravelPackage().getId(),
                        q.getPassengers(), q.getCurrency(), q.getTotalAmount(), q.getExpiresAt(), q.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminQuoteDetailResponse findById(UUID id) {
        return detail(quoteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found: " + id)));
    }

    private AdminQuoteDetailResponse detail(QuoteEntity q) {
        return new AdminQuoteDetailResponse(q.getId(), statusAt(q, clock.instant()), q.getOrigin(),
                ClientRef.of(q.getClient()), q.getTravelPackage() == null ? null : q.getTravelPackage().getId(),
                q.getPassengers(), q.getCurrency(), q.getTotalAmount(), q.getExpiresAt(), q.getCreatedAt(),
                q.getComponentsTotal(), q.getMarginPercent(), q.getMarginAmount(), q.getPromotionPercent(),
                q.getDiscountAmount(), q.getServiceFeeAmount(), q.getManualDiscountAmount(),
                q.getItems().stream().map(PricedItemResponse::of).toList(), q.getInternalNote(), q.getCreatedBy(),
                q.getStatus() == QuoteStatus.BOOKED
                        ? bookingRepository.findByQuoteId(q.getId()).map(BookingEntity::getId).orElse(null)
                        : null);
    }

    private static QuoteStatus statusAt(QuoteEntity quote, Instant now) {
        return quote.getStatus() == QuoteStatus.OPEN && !now.isBefore(quote.getExpiresAt())
                ? QuoteStatus.EXPIRED : quote.getStatus();
    }

    static Specification<QuoteEntity> toSpecification(AdminQuoteFilter filter, Instant now) {
        List<Specification<QuoteEntity>> specs = new ArrayList<>();
        if (filter.origin() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("origin"), filter.origin()));
        }
        if (filter.clientId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("client").get("id"), filter.clientId()));
        }
        if (filter.status() != null) {
            specs.add(switch (filter.status()) {
                case OPEN -> (root, query, cb) -> cb.and(cb.equal(root.get("status"), QuoteStatus.OPEN),
                        cb.greaterThan(root.get("expiresAt"), now));
                case EXPIRED -> (root, query, cb) -> cb.or(cb.equal(root.get("status"), QuoteStatus.EXPIRED),
                        cb.and(cb.equal(root.get("status"), QuoteStatus.OPEN),
                                cb.lessThanOrEqualTo(root.get("expiresAt"), now)));
                case BOOKED -> (root, query, cb) -> cb.equal(root.get("status"), QuoteStatus.BOOKED);
            });
        }
        return Specification.allOf(specs);
    }
}
