package br.com.familhas.trip;

import static br.com.familhas.trip.TripFixtures.booking;
import static br.com.familhas.trip.TripFixtures.category;
import static br.com.familhas.trip.TripFixtures.city;
import static br.com.familhas.trip.TripFixtures.client;
import static br.com.familhas.trip.TripFixtures.travelPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.payment.PaymentStatus;
import br.com.familhas.rating.RatingEntity;
import br.com.familhas.rating.RatingRepository;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.trip.dto.TripResponse;

class TripServiceTests {

    /** 13/11/2026 12:00 em São Paulo. */
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private TripService service;

    private ClientEntity ana;
    private TravelPackageEntity maceio;

    @BeforeEach
    void setUp() {
        service = new TripService(bookings, payments, ratings, Clock.fixed(NOW, ZoneOffset.UTC));
        ana = client();
        maceio = travelPackage("Maceio 7 noites", category("Praia"), city("Maceio", "Brasil"),
                LocalDateTime.of(2027, 3, 10, 6, 20));
        when(payments.findByBookingIdIn(anyCollection())).thenReturn(List.of());
        when(ratings.findByClient_Id(any())).thenReturn(List.of());
    }

    @Test
    void splitsUpcomingAndPastByLastDayAndSortsEach() {
        BookingEntity later = booking(ana, maceio, BookingStatus.CONFIRMED, date("2027-03-10"), date("2027-03-17"));
        BookingEntity sooner = booking(ana, null, BookingStatus.CONFIRMED, date("2026-12-01"), date("2026-12-05"));
        BookingEntity ongoing = booking(ana, null, BookingStatus.CONFIRMED, date("2026-11-10"), date("2026-11-13"));
        BookingEntity endedYesterday = booking(ana, maceio, BookingStatus.CONFIRMED, date("2026-11-05"),
                date("2026-11-12"));
        BookingEntity completedLongAgo = booking(ana, null, BookingStatus.COMPLETED, date("2026-01-05"),
                date("2026-01-10"));
        when(bookings.findByClientIdAndStatusIn(ana.getId(), TripService.TRIP_STATUSES))
                .thenReturn(List.of(later, completedLongAgo, sooner, endedYesterday, ongoing));

        var upcoming = service.findForClient(ana.getId(), TripTiming.UPCOMING, PageRequest.of(0, 20));
        var past = service.findForClient(ana.getId(), TripTiming.PAST, PageRequest.of(0, 20));

        assertThat(upcoming.content()).extracting(TripResponse::id)
                .containsExactly(ongoing.getId(), sooner.getId(), later.getId());
        assertThat(upcoming.content().getFirst().inProgress()).isTrue();
        assertThat(upcoming.content().get(1).inProgress()).isFalse();
        assertThat(past.content()).extracting(TripResponse::id)
                .containsExactly(endedYesterday.getId(), completedLongAgo.getId());
        assertThat(past.totalElements()).isEqualTo(2);
    }

    @Test
    void pagesInMemory() {
        List<BookingEntity> five = List.of(
                booking(ana, null, BookingStatus.CONFIRMED, date("2027-01-01"), date("2027-01-02")),
                booking(ana, null, BookingStatus.CONFIRMED, date("2027-02-01"), date("2027-02-02")),
                booking(ana, null, BookingStatus.CONFIRMED, date("2027-03-01"), date("2027-03-02")),
                booking(ana, null, BookingStatus.CONFIRMED, date("2027-04-01"), date("2027-04-02")),
                booking(ana, null, BookingStatus.CONFIRMED, date("2027-05-01"), date("2027-05-02")));
        when(bookings.findByClientIdAndStatusIn(ana.getId(), TripService.TRIP_STATUSES)).thenReturn(five);

        var page = service.findForClient(ana.getId(), TripTiming.UPCOMING, PageRequest.of(1, 2));

        assertThat(page.content()).extracting(TripResponse::startDate)
                .containsExactly(date("2027-03-01"), date("2027-04-01"));
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(service.findForClient(ana.getId(), TripTiming.UPCOMING, PageRequest.of(9, 2)).content())
                .isEmpty();
    }

    @Test
    void showsPaidAttemptWithInstallmentsEvenIfANewerAttemptExists() {
        BookingEntity trip = booking(ana, maceio, BookingStatus.CONFIRMED, date("2027-03-10"), date("2027-03-17"));
        PaymentEntity paid = PaymentEntity.pending(trip, PaymentMethod.PIX);
        paid.markSucceeded(NOW.minusSeconds(3600));
        PaymentEntity laterCanceled = PaymentEntity.pending(trip, PaymentMethod.CREDIT_CARD);
        laterCanceled.markCanceled();
        when(bookings.findByIdAndClientIdAndStatusIn(trip.getId(), ana.getId(), TripService.TRIP_STATUSES))
                .thenReturn(Optional.of(trip));
        when(payments.findByBookingIdIn(List.of(trip.getId()))).thenReturn(List.of(laterCanceled, paid));

        TripResponse response = service.findOneForClient(ana.getId(), trip.getId());

        assertThat(response.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.payment().method()).isEqualTo(PaymentMethod.PIX);
        assertThat(response.payment().installments()).isEqualTo(1);
        assertThat(response.payment().installmentPlan()).singleElement()
                .satisfies(i -> assertThat(i.amount()).isEqualByComparingTo("6000.00"));
        assertThat(response.travelPackage().title()).isEqualTo("Maceio 7 noites");
        assertThat(response.title()).isEqualTo("Maceio 7 noites");
    }

    @Test
    void pastTripWithPackageCanBeRatedOnceAndShowsTheRating() {
        BookingEntity past = booking(ana, maceio, BookingStatus.CONFIRMED, date("2026-10-01"), date("2026-10-08"));
        BookingEntity pastWithoutPackage = booking(ana, null, BookingStatus.COMPLETED, date("2026-09-01"),
                date("2026-09-03"));
        when(bookings.findByClientIdAndStatusIn(ana.getId(), TripService.TRIP_STATUSES))
                .thenReturn(List.of(past, pastWithoutPackage));

        var before = service.findForClient(ana.getId(), TripTiming.PAST, PageRequest.of(0, 20)).content();
        assertThat(before).extracting(TripResponse::canRate).containsExactly(true, false);
        assertThat(before.get(1).title()).isEqualTo("GOL - GRU / MCZ");

        RatingEntity rating = new RatingEntity(UUID.randomUUID(), 5, ana, maceio);
        when(ratings.findByClient_Id(ana.getId())).thenReturn(List.of(rating));
        var after = service.findForClient(ana.getId(), TripTiming.PAST, PageRequest.of(0, 20)).content();
        assertThat(after.getFirst().canRate()).isFalse();
        assertThat(after.getFirst().myRating()).isEqualTo(new TripResponse.MyRating(rating.getId(), 5));
    }

    @Test
    void upcomingTripCannotBeRated() {
        BookingEntity upcoming = booking(ana, maceio, BookingStatus.CONFIRMED, date("2027-03-10"),
                date("2027-03-17"));
        when(bookings.findByClientIdAndStatusIn(ana.getId(), TripService.TRIP_STATUSES))
                .thenReturn(List.of(upcoming));

        assertThat(service.findForClient(ana.getId(), TripTiming.UPCOMING, PageRequest.of(0, 20)).content())
                .singleElement().satisfies(trip -> assertThat(trip.canRate()).isFalse());
    }

    @Test
    void tripOfAnotherClientOrNotATripIs404WithCode() {
        UUID other = UUID.randomUUID();
        when(bookings.findByIdAndClientIdAndStatusIn(other, ana.getId(), TripService.TRIP_STATUSES))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOneForClient(ana.getId(), other))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("TRIP_NOT_FOUND");
                    assertThat(ex.getStatusCode().value()).isEqualTo(404);
                });
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }
}
