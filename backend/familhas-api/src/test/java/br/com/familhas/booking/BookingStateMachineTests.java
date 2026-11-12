package br.com.familhas.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingStateMachineTests {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDING_PAYMENT, CONFIRMED, true",
            "PENDING_PAYMENT, CANCELLED, true",
            "PENDING_PAYMENT, COMPLETED, false",
            "CONFIRMED, COMPLETED, true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, PENDING_PAYMENT, false",
            "CANCELLED, CONFIRMED, false",
            "CANCELLED, PENDING_PAYMENT, false",
            "COMPLETED, CANCELLED, false" })
    void transitions(BookingStatus from, BookingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void confirmSetsConfirmedAtOnlyOnce() {
        BookingEntity booking = pending();

        assertThat(booking.confirm(NOW)).isTrue();
        assertThat(booking.confirm(NOW.plusSeconds(60))).isFalse();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelledBookingCannotBeConfirmedByALateWebhook() {
        BookingEntity booking = pending();
        booking.cancel(CancellationReason.PAYMENT_EXPIRED, NOW);

        assertThat(booking.confirm(NOW.plusSeconds(60))).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_EXPIRED);
    }

    @Test
    void refundCancelsAConfirmedBooking() {
        BookingEntity booking = pending();
        booking.confirm(NOW);

        assertThat(booking.cancel(CancellationReason.REFUNDED, NOW.plusSeconds(60))).isTrue();
        assertThat(booking.getCancelledAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void paymentWindowClosesAtExpiresAt() {
        BookingEntity booking = pending();

        assertThat(booking.isPaymentWindowOpen(NOW.minusSeconds(1))).isTrue();
        assertThat(booking.isPaymentWindowOpen(NOW)).isFalse();
    }

    @Test
    void heldSeatsAreReturnedOnlyOnce() {
        BookingEntity booking = pending();
        booking.setHeldSeats(2);

        assertThat(booking.takeHeldSeats()).isEqualTo(2);
        assertThat(booking.takeHeldSeats()).isZero();
    }

    private static BookingEntity pending() {
        BookingEntity booking = new BookingEntity();
        booking.setExpiresAt(NOW);
        return booking;
    }
}
