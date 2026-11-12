package br.com.familhas.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.familhas.booking.BookingEntity;

class PaymentStateMachineTests {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00Z");

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PENDING, PROCESSING, true",
            "PENDING, SUCCEEDED, true",
            "PENDING, FAILED, true",
            "PENDING, CANCELED, true",
            "PENDING, REFUNDED, false",
            "PROCESSING, SUCCEEDED, true",
            "PROCESSING, FAILED, true",
            "PROCESSING, PENDING, false",
            "FAILED, SUCCEEDED, true",
            "FAILED, PROCESSING, true",
            "FAILED, CANCELED, true",
            "SUCCEEDED, FAILED, false",
            "SUCCEEDED, CANCELED, false",
            "SUCCEEDED, PARTIALLY_REFUNDED, true",
            "SUCCEEDED, REFUNDED, true",
            "PARTIALLY_REFUNDED, PARTIALLY_REFUNDED, true",
            "PARTIALLY_REFUNDED, REFUNDED, true",
            "REFUNDED, PARTIALLY_REFUNDED, false",
            "CANCELED, SUCCEEDED, false" })
    void transitions(PaymentStatus from, PaymentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void newPaymentIsSingleInstallmentOfTheBookingTotalWithItsOwnIdempotencyKey() {
        PaymentEntity payment = PaymentEntity.pending(booking("7830.16"), PaymentMethod.CREDIT_CARD);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("7830.16");
        assertThat(payment.getInstallments()).isEqualTo(1);
        assertThat(payment.getInstallmentPlan()).singleElement()
                .satisfies(i -> assertThat(i.getAmount()).isEqualByComparingTo("7830.16"));
        assertThat(payment.getIdempotencyKey()).isEqualTo("checkout-session:" + payment.getId());
        assertThat(payment.idempotencyKeyFor("refund")).isEqualTo("refund:" + payment.getId());
        assertThat(payment.isNew()).isTrue();
    }

    @Test
    void declinedCardCanStillSucceedInTheSameSession() {
        PaymentEntity payment = PaymentEntity.pending(booking("100.00"), PaymentMethod.CREDIT_CARD);
        payment.setSessionExpiresAt(NOW.plusSeconds(600));

        assertThat(payment.markFailed("card_declined")).isTrue();
        assertThat(payment.hasOpenSession(NOW)).isTrue();
        assertThat(payment.markSucceeded(NOW)).isTrue();

        assertThat(payment.getFailureCode()).isNull();
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    void lateFailureAfterSuccessIsIgnored() {
        PaymentEntity payment = PaymentEntity.pending(booking("100.00"), PaymentMethod.CREDIT_CARD);
        payment.markSucceeded(NOW);

        assertThat(payment.markFailed("card_declined")).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void processingPixIsActiveEvenAfterTheSessionExpired() {
        PaymentEntity payment = PaymentEntity.pending(booking("100.00"), PaymentMethod.PIX);
        payment.setSessionExpiresAt(NOW.minusSeconds(1));
        assertThat(payment.isActive(NOW)).isFalse();

        payment.markProcessing();

        assertThat(payment.isActive(NOW)).isTrue();
        assertThat(payment.hasOpenSession(NOW)).isFalse();
    }

    @Test
    void partialThenFullRefund() {
        PaymentEntity payment = PaymentEntity.pending(booking("100.00"), PaymentMethod.CREDIT_CARD);
        payment.markSucceeded(NOW);

        assertThat(payment.applyRefund(new BigDecimal("30.00"))).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.applyRefund(new BigDecimal("30.00"))).as("mesmo total, evento repetido").isFalse();
        assertThat(payment.applyRefund(new BigDecimal("100.00"))).isTrue();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("100.00");
    }

    private static BookingEntity booking(String total) {
        BookingEntity booking = new BookingEntity();
        booking.setTotalAmount(new BigDecimal(total));
        booking.setCurrency("BRL");
        return booking;
    }
}
