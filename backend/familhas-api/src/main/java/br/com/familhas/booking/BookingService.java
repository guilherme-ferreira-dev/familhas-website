package br.com.familhas.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.dto.BookingRequest;
import br.com.familhas.booking.dto.BookingResponse;
import br.com.familhas.booking.dto.BookingSummaryResponse;
import br.com.familhas.booking.event.BookingCreatedEvent;
import br.com.familhas.common.Currencies;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.messaging.OutboxService;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.provider.ProviderGateway;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.flight.CuratedFlightProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.quote.QuoteEntity;
import br.com.familhas.quote.QuoteItemEntity;
import br.com.familhas.quote.QuoteRepository;
import br.com.familhas.quote.QuoteStatus;
import br.com.familhas.quote.dto.QuoteResponse;

/**
 * Reserva a partir da cotação (guia, Fase 6; ADR 0007, D3).
 *
 * - Só cotação OPEN, dentro da validade, em BRL e do próprio cliente. A cotação é travada para que duas
 *   requisições simultâneas não gerem duas reservas.
 * - As ofertas são reconsultadas no provedor (sem cache). O preço continua o da cotação.
 * - Os assentos do voo do catálogo curado são bloqueados até o pagamento ou o cancelamento.
 */
@Service
public class BookingService {

    static final Set<String> SORTABLE = Set.of("createdAt", "totalAmount");

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final QuoteRepository quoteRepository;
    private final BookingRepository bookingRepository;
    private final FlightRepository flightRepository;
    private final PaymentRepository paymentRepository;
    private final ProviderGateway gateway;
    private final List<FlightProvider> flightProviders;
    private final List<HotelProvider> hotelProviders;
    private final List<CarRentalProvider> carProviders;
    private final OutboxService outboxService;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingService(QuoteRepository quoteRepository, BookingRepository bookingRepository,
            FlightRepository flightRepository, PaymentRepository paymentRepository, ProviderGateway gateway,
            List<FlightProvider> flightProviders,
            List<HotelProvider> hotelProviders, List<CarRentalProvider> carProviders, OutboxService outboxService,
            BookingProperties properties, Clock clock) {
        this.quoteRepository = quoteRepository;
        this.bookingRepository = bookingRepository;
        this.flightRepository = flightRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.flightProviders = flightProviders;
        this.hotelProviders = hotelProviders;
        this.carProviders = carProviders;
        this.outboxService = outboxService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(UUID clientId, BookingRequest request) {
        Instant now = clock.instant();
        QuoteEntity quote = quoteRepository.lockByIdAndClientId(request.quoteId(), clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Quote not found: " + request.quoteId()));
        if (quote.getStatus() == QuoteStatus.BOOKED) {
            UUID bookingId = bookingRepository.findByQuoteId(quote.getId()).map(BookingEntity::getId).orElse(null);
            throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_ALREADY_BOOKED", "Esta cotacao ja virou reserva.",
                    bookingId == null ? Map.of() : Map.of("bookingId", bookingId));
        }
        if (quote.getStatus() == QuoteStatus.EXPIRED || !now.isBefore(quote.getExpiresAt())) {
            throw new BusinessException(HttpStatus.CONFLICT, "QUOTE_EXPIRED",
                    "A cotacao expirou. Gere uma nova cotacao.");
        }
        if (!Currencies.DEFAULT.equals(quote.getCurrency())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_NOT_SUPPORTED",
                    "O pagamento so aceita BRL. Esta cotacao esta em " + quote.getCurrency() + ".");
        }

        BookingEntity booking = new BookingEntity();
        booking.setClient(quote.getClient());
        booking.setQuote(quote);
        booking.setTravelPackage(quote.getTravelPackage());
        booking.setPassengers(quote.getPassengers());
        booking.setCurrency(quote.getCurrency());
        booking.setComponentsTotal(quote.getComponentsTotal());
        booking.setMarginPercent(quote.getMarginPercent());
        booking.setMarginAmount(quote.getMarginAmount());
        booking.setPromotionPercent(quote.getPromotionPercent());
        booking.setDiscountAmount(quote.getDiscountAmount());
        booking.setServiceFeeAmount(quote.getServiceFeeAmount());
        booking.setTotalAmount(quote.getTotalAmount());
        booking.setExpiresAt(now.plus(properties.paymentWindow()));
        for (QuoteItemEntity item : quote.getItems()) {
            reverify(item, quote.getPassengers(), booking);
            booking.addItem(BookingItemEntity.copyOf(item));
        }

        quote.setStatus(QuoteStatus.BOOKED);
        BookingEntity saved = bookingRepository.saveAndFlush(booking);
        outboxService.append(BookingCreatedEvent.of(saved, now));
        log.info("Reserva criada: bookingId={} quoteId={} heldSeats={}", saved.getId(), quote.getId(),
                saved.getHeldSeats());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse findForClient(UUID clientId, UUID bookingId) {
        BookingEntity booking = bookingRepository.findByIdAndClientId(bookingId, clientId)
                .orElseThrow(() -> notFound(bookingId));
        return toResponse(booking, paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .map(BookingService::toPayment).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingSummaryResponse> findAllForClient(UUID clientId, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        var page = bookingRepository.findByClientId(clientId, pageable);
        Map<UUID, PaymentEntity> latestPayments = page.isEmpty() ? Map.of()
                : paymentRepository.findLatestByBookingIds(page.map(BookingEntity::getId).toList()).stream()
                        .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity(), (a, b) -> a));
        return PageResponse.of(page, booking ->
                new BookingSummaryResponse(
                        booking.getId(),
                        booking.getStatus(),
                        booking.getTravelPackage() == null ? null : booking.getTravelPackage().getId(),
                        booking.getPassengers(),
                        booking.getCurrency(),
                        booking.getTotalAmount(),
                        booking.getItems().isEmpty() ? null : booking.getItems().getFirst().getStartDate(),
                        latestPayments.containsKey(booking.getId())
                                ? latestPayments.get(booking.getId()).getStatus() : null,
                        booking.getCreatedAt()));
    }

    /**
     * Devolve os assentos bloqueados pela reserva. Chamado na mesma transação do cancelamento, com a reserva
     * travada; repetir não devolve de novo.
     */
    public void releaseSeats(BookingEntity booking) {
        int seats = booking.takeHeldSeats();
        if (seats > 0 && booking.getHeldFlightId() != null) {
            flightRepository.releaseSeats(booking.getHeldFlightId(), seats);
            log.info("Assentos devolvidos: bookingId={} flightId={} seats={}", booking.getId(),
                    booking.getHeldFlightId(), seats);
        }
    }

    /** Reconsulta a oferta no provedor; no voo do catálogo curado, bloqueia os assentos. */
    private void reverify(QuoteItemEntity item, int passengers, BookingEntity booking) {
        switch (item.getItemType()) {
            case FLIGHT -> {
                FlightOffer flight = gateway.findOffer(flightProviders, item.getOfferId())
                        .orElseThrow(BookingService::offerUnavailable);
                var curatedFlightId = CuratedFlightProvider.flightIdOf(item.getOfferId());
                if (curatedFlightId.isPresent()) {
                    if (flightRepository.holdSeats(curatedFlightId.get(), passengers) == 0) {
                        throw seatsUnavailable();
                    }
                    booking.setHeldFlightId(curatedFlightId.get());
                    booking.setHeldSeats(passengers);
                } else if (flight.seatsAvailable() < passengers) {
                    throw seatsUnavailable();
                }
            }
            case HOTEL_STAY -> gateway.findOffer(hotelProviders, item.getOfferId())
                    .orElseThrow(BookingService::offerUnavailable);
            case CAR_RENTAL -> gateway.findOffer(carProviders, item.getOfferId())
                    .orElseThrow(BookingService::offerUnavailable);
        }
    }

    static BookingResponse toResponse(BookingEntity booking) {
        return toResponse(booking, null);
    }

    static BookingResponse toResponse(BookingEntity booking, BookingResponse.Payment payment) {
        List<QuoteResponse.Item> items = booking.getItems().stream()
                .map(i -> new QuoteResponse.Item(i.getItemType(), i.getDescription(), i.getStartDate(),
                        i.getEndDate(), i.getQuantity()))
                .toList();
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getQuote().getId(),
                booking.getTravelPackage() == null ? null : booking.getTravelPackage().getId(),
                booking.getPassengers(),
                items,
                booking.getCurrency(),
                booking.getComponentsTotal().add(booking.getMarginAmount()),
                booking.getDiscountAmount(),
                booking.getServiceFeeAmount(),
                booking.getTotalAmount(),
                booking.getExpiresAt(),
                booking.getConfirmedAt(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                payment,
                booking.getCreatedAt());
    }

    static BookingResponse.Payment toPayment(PaymentEntity payment) {
        return new BookingResponse.Payment(payment.getId(), payment.getMethod(), payment.getStatus(),
                payment.getInstallments(), payment.getAmount(), payment.getCurrency(), payment.getRefundedAmount(),
                payment.getPaidAt());
    }

    static ResponseStatusException notFound(UUID bookingId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + bookingId);
    }

    private static BusinessException offerUnavailable() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "OFFER_UNAVAILABLE",
                "Uma das ofertas da cotacao nao esta mais disponivel. Gere uma nova cotacao.");
    }

    private static BusinessException seatsUnavailable() {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "SEATS_UNAVAILABLE",
                "O voo nao tem mais assentos para todos os passageiros.");
    }
}
