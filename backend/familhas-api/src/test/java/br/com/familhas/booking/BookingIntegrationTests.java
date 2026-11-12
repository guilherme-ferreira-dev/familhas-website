package br.com.familhas.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.dto.BookingRequest;
import br.com.familhas.booking.dto.BookingResponse;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.provider.hotel.CuratedHotelProvider;
import br.com.familhas.quote.QuoteRepository;
import br.com.familhas.quote.QuoteService;
import br.com.familhas.quote.QuoteStatus;
import br.com.familhas.quote.dto.QuoteRequest;
import br.com.familhas.quote.dto.QuoteRequest.HotelSelection;

/** Reserva ponta a ponta no Oracle real (V11 + seed): snapshot, bloqueio de assentos, BRL e uma reserva por cotação. */
@SpringBootTest(properties = "familhas.outbox.publisher-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class BookingIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID ANA = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");
    static final UUID ROOM_ORLANDO_USD = UUID.fromString("ee2e0000-0000-4000-8000-000000000005");

    @Autowired private QuoteService quoteService;
    @Autowired private BookingService bookingService;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private FlightRepository flightRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void packageQuoteBecomesPendingBookingAndHoldsTheFlightSeats() {
        int seatsBefore = seats(FLIGHT_GOL);
        UUID quoteId = quoteService.create(ANA, new QuoteRequest(PACKAGE_MACEIO, 2, null, null, null)).id();

        BookingResponse booking = bookingService.create(ANA, new BookingRequest(quoteId));

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.total()).isEqualByComparingTo("7830.16");
        assertThat(booking.items()).hasSize(3);
        assertThat(seats(FLIGHT_GOL)).isEqualTo(seatsBefore - 2);
        assertThat(quoteRepository.findById(quoteId)).get()
                .extracting(q -> q.getStatus()).isEqualTo(QuoteStatus.BOOKED);
        assertThat(bookingService.findForClient(ANA, booking.id()).quoteId()).isEqualTo(quoteId);
    }

    @Test
    void sameQuoteCannotBeBookedTwice() {
        UUID quoteId = quoteService.create(ANA, new QuoteRequest(PACKAGE_MACEIO, 1, null, null, null)).id();
        BookingResponse first = bookingService.create(ANA, new BookingRequest(quoteId));
        int seatsAfterFirst = seats(FLIGHT_GOL);

        assertThatThrownBy(() -> bookingService.create(ANA, new BookingRequest(quoteId)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("QUOTE_ALREADY_BOOKED");
                    assertThat(ex.getBody().getProperties()).containsEntry("bookingId", first.id());
                });
        assertThat(seats(FLIGHT_GOL)).isEqualTo(seatsAfterFirst);
    }

    @Test
    void quoteInDollarsIsRejectedWithoutTouchingTheQuote() {
        LocalDate checkIn = LocalDate.of(2027, 7, 1);
        UUID quoteId = quoteService.create(ANA, new QuoteRequest(null, 2, null,
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ORLANDO_USD), checkIn, checkIn.plusDays(5)),
                null)).id();

        assertThatThrownBy(() -> bookingService.create(ANA, new BookingRequest(quoteId)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("CURRENCY_NOT_SUPPORTED"));
        assertThat(quoteRepository.findById(quoteId)).get()
                .extracting(q -> q.getStatus()).isEqualTo(QuoteStatus.OPEN);
    }

    @Test
    void releasingSeatsReturnsThemToTheFlight() {
        UUID quoteId = quoteService.create(ANA, new QuoteRequest(PACKAGE_MACEIO, 2, null, null, null)).id();
        UUID bookingId = bookingService.create(ANA, new BookingRequest(quoteId)).id();
        int seatsHeld = seats(FLIGHT_GOL);

        transactionTemplate.executeWithoutResult(status -> {
            BookingEntity booking = bookingRepository.lockById(bookingId).orElseThrow();
            booking.cancel(CancellationReason.PAYMENT_EXPIRED, java.time.Instant.now());
            bookingService.releaseSeats(booking);
        });

        assertThat(seats(FLIGHT_GOL)).isEqualTo(seatsHeld + 2);
        assertThat(bookingRepository.findById(bookingId)).get()
                .satisfies(b -> assertThat(b.getHeldSeats()).isZero());
    }

    private int seats(UUID flightId) {
        return flightRepository.findById(flightId).orElseThrow().getSeatsAvailable();
    }
}
