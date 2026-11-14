package br.com.familhas.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingItemEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.common.PageResponse;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.rating.RatingEntity;
import br.com.familhas.rating.RatingRepository;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;
import br.com.familhas.trip.dto.TripResponse;

/**
 * "Minhas viagens" (guia, Fase 8; ADR 0010): leitura das reservas CONFIRMED ou COMPLETED do cliente,
 * sem tabela própria. Pagamentos, parcelas e avaliações do cliente são buscados em lote.
 */
@Service
@Transactional(readOnly = true)
public class TripService {

    public static final Set<BookingStatus> TRIP_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private static final Comparator<TripResponse> UPCOMING_ORDER = Comparator
            .comparing(TripResponse::startDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TripResponse::id);
    private static final Comparator<TripResponse> PAST_ORDER = Comparator
            .comparing(TripResponse::endDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TripResponse::id);

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RatingRepository ratingRepository;
    private final Clock clock;

    public TripService(BookingRepository bookingRepository, PaymentRepository paymentRepository,
            RatingRepository ratingRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.ratingRepository = ratingRepository;
        this.clock = clock;
    }

    /** Paginação em memória: um cliente tem poucas reservas (ADR 0010, D3). */
    public PageResponse<TripResponse> findForClient(UUID clientId, TripTiming timing, Pageable pageable) {
        List<BookingEntity> bookings = bookingRepository.findByClientIdAndStatusIn(clientId, TRIP_STATUSES);
        List<TripResponse> trips = toResponses(clientId, bookings).stream()
                .filter(trip -> trip.timing() == timing)
                .sorted(timing == TripTiming.UPCOMING ? UPCOMING_ORDER : PAST_ORDER)
                .toList();
        int size = pageable.getPageSize();
        int page = pageable.getPageNumber();
        int from = (int) Math.min((long) page * size, trips.size());
        int to = Math.min(from + size, trips.size());
        int totalPages = (int) Math.ceil((double) trips.size() / size);
        return new PageResponse<>(trips.subList(from, to), page, size, trips.size(), totalPages);
    }

    /** Viagem de outro cliente, ou reserva que não é viagem (pendente, cancelada), responde 404. */
    public TripResponse findOneForClient(UUID clientId, UUID tripId) {
        BookingEntity booking = bookingRepository.findByIdAndClientIdAndStatusIn(tripId, clientId, TRIP_STATUSES)
                .orElseThrow(() -> notFound(tripId));
        return toResponses(clientId, List.of(booking)).getFirst();
    }

    public static BusinessException notFound(UUID tripId) {
        return new BusinessException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Viagem nao encontrada: " + tripId);
    }

    private List<TripResponse> toResponses(UUID clientId, List<BookingEntity> bookings) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        LocalDate today = BusinessTime.today(clock);
        Map<UUID, PaymentEntity> payments = paymentOfEachBooking(bookings.stream().map(BookingEntity::getId).toList());
        Map<UUID, RatingEntity> ratingsByPackage = ratingRepository.findByClient_Id(clientId).stream()
                .collect(Collectors.toMap(r -> r.getTravelPackage().getId(), Function.identity(), (a, b) -> a));
        return bookings.stream()
                .map(booking -> toResponse(booking, payments.get(booking.getId()), ratingsByPackage, today))
                .toList();
    }

    /** O pagamento mostrado: o último que recebeu dinheiro; sem nenhum, a tentativa mais recente (ADR 0010, D4). */
    private Map<UUID, PaymentEntity> paymentOfEachBooking(Collection<UUID> bookingIds) {
        Comparator<PaymentEntity> preference = Comparator
                .comparing((PaymentEntity p) -> p.getPaidAt() != null)
                .thenComparing(p -> p.getPaidAt() == null ? p.getCreatedAt() : p.getPaidAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        return paymentRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(p -> p.getBooking().getId(), Function.identity(),
                        (a, b) -> preference.compare(a, b) >= 0 ? a : b));
    }

    private static TripResponse toResponse(BookingEntity booking, PaymentEntity payment,
            Map<UUID, RatingEntity> ratingsByPackage, LocalDate today) {
        List<BookingItemEntity> items = booking.getItems();
        LocalDate start = items.stream().map(BookingItemEntity::getStartDate).min(Comparator.naturalOrder())
                .orElse(null);
        LocalDate end = items.stream().map(BookingItemEntity::getEndDate).max(Comparator.naturalOrder())
                .orElse(null);
        TripTiming timing = TripTiming.of(booking.getStatus(), end, today);
        boolean inProgress = timing == TripTiming.UPCOMING && start != null && !start.isAfter(today);

        var travelPackage = booking.getTravelPackage();
        RatingEntity rating = travelPackage == null ? null : ratingsByPackage.get(travelPackage.getId());
        boolean canRate = timing == TripTiming.PAST && travelPackage != null && rating == null;
        String title = travelPackage != null ? travelPackage.getTitle()
                : items.isEmpty() ? "Viagem" : items.getFirst().getDescription();

        return new TripResponse(
                booking.getId(),
                booking.getStatus(),
                timing,
                inProgress,
                start,
                end,
                title,
                booking.getPassengers(),
                travelPackage == null ? null : PackageSummaryResponse.of(travelPackage),
                items.stream().map(i -> new TripResponse.Item(i.getItemType(), i.getDescription(), i.getStartDate(),
                        i.getEndDate(), i.getQuantity())).toList(),
                booking.getCurrency(),
                booking.getTotalAmount(),
                booking.getConfirmedAt(),
                payment == null ? null : toPayment(payment),
                rating == null ? null : new TripResponse.MyRating(rating.getId(), rating.getRating()),
                canRate);
    }

    private static TripResponse.Payment toPayment(PaymentEntity payment) {
        return new TripResponse.Payment(payment.getMethod(), payment.getStatus(), payment.getInstallments(),
                payment.getAmount(), payment.getCurrency(), payment.getRefundedAmount(), payment.getPaidAt(),
                payment.getInstallmentPlan().stream()
                        .map(i -> new TripResponse.Installment(i.getNumber(), i.getAmount(), i.getCurrency()))
                        .toList());
    }
}
