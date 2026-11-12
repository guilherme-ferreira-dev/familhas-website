package br.com.familhas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingService;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;
import br.com.familhas.payment.PaymentNotification.Kind;
import jakarta.persistence.EntityManager;

class PaymentNotificationProcessorTests {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final List<PaymentEntity> attempts = new ArrayList<>();
    private PaymentNotificationProcessor processor;
    private BookingEntity booking;
    private PaymentEntity payment;

    @BeforeEach
    void setUp() {
        processor = new PaymentNotificationProcessor(payments, bookings, bookingService, gateway,
                mock(EntityManager.class), Clock.fixed(NOW, ZoneOffset.UTC));
        booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setTotalAmount(new BigDecimal("7830.16"));
        booking.setCurrency("BRL");
        booking.setExpiresAt(NOW.plusSeconds(600));
        payment = attempt(PaymentMethod.CREDIT_CARD);
        when(bookings.lockById(booking.getId())).thenReturn(Optional.of(booking));
        when(payments.findByBookingIdOrderByCreatedAtDesc(booking.getId())).thenReturn(attempts);
    }

    @Test
    void paidSessionConfirmsTheBookingAndKeepsThePaymentIntent() {
        assertThat(processor.apply(notification(Kind.SUCCEEDED, "7830.16"))).isTrue();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void confirmationExpiresTheOtherOpenSession() {
        PaymentEntity pix = attempt(PaymentMethod.PIX);

        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        verify(gateway).expireCheckoutSession(pix.getStripeCheckoutSessionId(), "expire:" + pix.getId());
        assertThat(pix.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void divergentAmountConfirmsNothing() {
        processor.apply(notification(Kind.SUCCEEDED, "1.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void lateSuccessOnACancelledBookingIsRefundedAutomatically() {
        booking.cancel(CancellationReason.PAYMENT_EXPIRED, NOW);

        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(gateway).refund("pi_1", "refund:" + payment.getId());
    }

    @Test
    void secondPaymentOfAnAlreadyPaidBookingIsRefunded() {
        PaymentEntity firstPaid = attempt(PaymentMethod.PIX);
        firstPaid.markSucceeded(NOW);
        booking.confirm(NOW);
        payment.markCanceled();

        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        assertThat(payment.getStatus()).as("o dinheiro entrou: registrado").isEqualTo(PaymentStatus.SUCCEEDED);
        verify(gateway).refund("pi_1", "refund:" + payment.getId());
    }

    @Test
    void repeatedSuccessDoesNothingTheSecondTime() {
        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));
        Instant confirmedAt = booking.getConfirmedAt();

        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        assertThat(booking.getConfirmedAt()).isEqualTo(confirmedAt);
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void lateFailureAfterSuccessIsIgnored() {
        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        processor.apply(new PaymentNotification("evt_2", "payment_intent.payment_failed", Kind.FAILED,
                payment.getId(), null, "pi_1", null, null, "BRL", "card_declined"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void fullRefundCancelsTheBookingAndReleasesSeats() {
        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        processor.apply(refund("7830.16"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo(CancellationReason.REFUNDED);
        verify(bookingService).releaseSeats(booking);
    }

    @Test
    void partialRefundKeepsTheBookingConfirmed() {
        processor.apply(notification(Kind.SUCCEEDED, "7830.16"));

        processor.apply(refund("1000.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("1000.00");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void refundOfADuplicatePaymentDoesNotCancelTheBookingPaidByTheOther() {
        PaymentEntity other = attempt(PaymentMethod.PIX);
        other.markSucceeded(NOW);
        booking.confirm(NOW);
        payment.markSucceeded(NOW);
        payment.setStripePaymentIntentId("pi_1");

        processor.apply(refund("7830.16"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingService, never()).releaseSeats(any());
    }

    @Test
    void notificationOfAnUnknownPaymentIsIgnored() {
        assertThat(processor.apply(new PaymentNotification("evt_x", "payment_intent.succeeded", Kind.SUCCEEDED,
                null, null, "pi_desconhecido", new BigDecimal("1.00"), null, "BRL", null))).isFalse();
        verify(bookings, never()).lockById(any());
    }

    private PaymentNotification notification(Kind kind, String amount) {
        return new PaymentNotification("evt_" + UUID.randomUUID(), "checkout.session.completed", kind,
                payment.getId(), payment.getStripeCheckoutSessionId(), "pi_1", new BigDecimal(amount), null, "BRL",
                null);
    }

    private PaymentNotification refund(String totalRefunded) {
        when(payments.findFirstByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment));
        return new PaymentNotification("evt_" + UUID.randomUUID(), "charge.refunded", Kind.REFUNDED, null, null,
                "pi_1", new BigDecimal("7830.16"), new BigDecimal(totalRefunded), "BRL", null);
    }

    private PaymentEntity attempt(PaymentMethod method) {
        PaymentEntity attempt = PaymentEntity.pending(booking, method);
        attempt.setStripeCheckoutSessionId("cs_test_" + attempt.getId());
        attempt.setSessionExpiresAt(NOW.plusSeconds(1200));
        attempts.add(attempt);
        when(payments.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        return attempt;
    }
}
