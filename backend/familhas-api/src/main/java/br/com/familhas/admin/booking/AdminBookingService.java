package br.com.familhas.admin.booking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.admin.AdminPeriod;
import br.com.familhas.admin.ClientRef;
import br.com.familhas.admin.PricedItemResponse;
import br.com.familhas.admin.payment.AdminPaymentResponse;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.payment.PaymentStatus;

/** Reservas no Admin, só leitura (ADR 0011). */
@Service
@Transactional(readOnly = true)
public class AdminBookingService {

    static final Set<String> SORTABLE = Set.of("createdAt", "totalAmount", "expiresAt", "status");

    private final AdminBookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;

    public AdminBookingService(AdminBookingRepository bookingRepository, PaymentRepository paymentRepository) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    /** Cliente, pacote e itens vêm em lote (default_batch_fetch_size); o último pagamento, numa query só. */
    public PageResponse<AdminBookingSummaryResponse> findAll(AdminBookingFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        Page<BookingEntity> page = bookingRepository.findAll(toSpecification(filter), pageable);
        Map<UUID, PaymentStatus> latest = page.isEmpty() ? Map.of()
                : paymentRepository.findLatestByBookingIds(page.map(BookingEntity::getId).toList()).stream()
                        .collect(Collectors.toMap(p -> p.getBooking().getId(), PaymentEntity::getStatus,
                                (a, b) -> a));
        return PageResponse.of(page, booking -> summary(booking, latest.get(booking.getId())));
    }

    public AdminBookingDetailResponse findById(UUID id) {
        BookingEntity b = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
        List<AdminPaymentResponse> payments = paymentRepository.findByBookingIdOrderByCreatedAtDesc(id).stream()
                .map(AdminPaymentResponse::of).toList();
        AdminBookingSummaryResponse s = summary(b, payments.isEmpty() ? null : payments.getFirst().status());
        return new AdminBookingDetailResponse(s.id(), s.status(), s.client(), s.travelPackageId(),
                s.travelPackageTitle(), s.passengers(), s.currency(), s.totalAmount(), s.startDate(), s.expiresAt(),
                s.latestPaymentStatus(), s.createdAt(), b.getComponentsTotal(), b.getMarginPercent(),
                b.getMarginAmount(), b.getPromotionPercent(), b.getDiscountAmount(), b.getServiceFeeAmount(),
                b.getQuote().getId(), b.getConfirmedAt(), b.getCancelledAt(), b.getCancellationReason(),
                b.getItems().stream().map(PricedItemResponse::of).toList(), payments);
    }

    private static AdminBookingSummaryResponse summary(BookingEntity b, PaymentStatus latestPayment) {
        return new AdminBookingSummaryResponse(
                b.getId(),
                b.getStatus(),
                ClientRef.of(b.getClient()),
                b.getTravelPackage() == null ? null : b.getTravelPackage().getId(),
                b.getTravelPackage() == null ? null : b.getTravelPackage().getTitle(),
                b.getPassengers(),
                b.getCurrency(),
                b.getTotalAmount(),
                b.getItems().isEmpty() ? null : b.getItems().getFirst().getStartDate(),
                b.getExpiresAt(),
                latestPayment,
                b.getCreatedAt());
    }

    static Specification<BookingEntity> toSpecification(AdminBookingFilter filter) {
        List<Specification<BookingEntity>> specs = new ArrayList<>();
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.clientId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("client").get("id"), filter.clientId()));
        }
        AdminPeriod period = AdminPeriod.of(filter.from(), filter.to());
        if (period.start() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), period.start()));
        }
        if (period.endExclusive() != null) {
            specs.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), period.endExclusive()));
        }
        return Specification.allOf(specs);
    }
}
