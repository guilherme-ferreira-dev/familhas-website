package br.com.familhas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingService;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;
import br.com.familhas.payment.PaymentNotification.Kind;

class PaymentReconciliationServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingService bookingService = mock(BookingService.class);
    private final PaymentNotificationProcessor processor = mock(PaymentNotificationProcessor.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final List<PaymentEntity> attempts = new ArrayList<>();
    private PaymentReconciliationService service;
    private BookingEntity booking;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationService(payments, bookings, bookingService, processor, gateway,
                new PaymentProperties(null, null, null, null, null, null, 0), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(PlatformTransactionManager.class));
        booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setCurrency("BRL");
        booking.setExpiresAt(NOW.minusSeconds(60));
        when(gateway.isAvailable()).thenReturn(true);
        when(bookings.lockById(booking.getId())).thenReturn(Optional.of(booking));
        when(bookings.findIdsByStatusAndExpiresAtBefore(eq(BookingStatus.PENDING_PAYMENT), eq(NOW), any()))
                .thenReturn(List.of(booking.getId()));
        when(payments.findByBookingIdOrderByCreatedAtDesc(booking.getId())).thenReturn(attempts);
        when(payments.findStaleIds(anyCollection(), any(), any())).thenReturn(List.of());
    }

    @Test
    void expiredBookingWithoutActivePaymentIsCancelledAndSeatsReturn() {
        PaymentEntity expiredSession = attempt(NOW.minusSeconds(10));
        when(gateway.fetchCheckoutSessionState(expiredSession.getStripeCheckoutSessionId())).thenReturn(Optional.empty());

        PaymentReconciliationService.Result result = service.reconcile();

        assertThat(result.bookingsCancelled()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancellationReason()).isEqualTo(CancellationReason.PAYMENT_EXPIRED);
        assertThat(expiredSession.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(bookingService).releaseSeats(booking);
    }

    @Test
    void openSessionKeepsTheBookingAfterItsDeadline() {
        attempt(NOW.plusSeconds(600));
        when(gateway.fetchCheckoutSessionState(any())).thenReturn(Optional.empty());

        assertThat(service.reconcile().bookingsCancelled()).isZero();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(bookingService, never()).releaseSeats(any());
    }

    @Test
    void lastSecondPaymentIsAppliedBeforeDecidingToCancel() {
        PaymentEntity paid = attempt(NOW.minusSeconds(10));
        when(gateway.fetchCheckoutSessionState(paid.getStripeCheckoutSessionId())).thenReturn(Optional.of(
                new PaymentNotification(null, "reconciliation", Kind.SUCCEEDED, null, paid.getStripeCheckoutSessionId(),
                        "pi_1", new BigDecimal("100.00"), null, "BRL", null)));
        when(processor.apply(any())).thenAnswer(i -> booking.confirm(NOW));

        assertThat(service.reconcile().bookingsCancelled()).isZero();

        ArgumentCaptor<PaymentNotification> applied = ArgumentCaptor.forClass(PaymentNotification.class);
        verify(processor).apply(applied.capture());
        assertThat(applied.getValue().paymentId()).isEqualTo(paid.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void stalePaymentIsRefreshedFromStripe() {
        PaymentEntity stale = attempt(NOW.plusSeconds(600));
        stale.markProcessing();
        when(payments.findStaleIds(anyCollection(), eq(NOW.minusSeconds(600)), any()))
                .thenReturn(List.of(stale.getId()));
        when(payments.findById(stale.getId())).thenReturn(Optional.of(stale));
        when(gateway.fetchCheckoutSessionState(stale.getStripeCheckoutSessionId())).thenReturn(Optional.of(
                new PaymentNotification(null, "reconciliation", Kind.FAILED, null, stale.getStripeCheckoutSessionId(),
                        "pi_1", null, null, "BRL", "async_payment_failed")));
        when(bookings.findIdsByStatusAndExpiresAtBefore(any(), any(), any())).thenReturn(List.of());

        assertThat(service.reconcile().paymentsChecked()).isEqualTo(1);
        verify(processor).apply(any());
    }

    @Test
    void withoutStripeOnlyExpiresBookings() {
        when(gateway.isAvailable()).thenReturn(false);
        attempt(NOW.minusSeconds(10));

        assertThat(service.reconcile().bookingsCancelled()).isEqualTo(1);
        verify(payments, never()).findStaleIds(anyCollection(), any(), any());
        verify(gateway, never()).fetchCheckoutSessionState(any());
    }

    @Test
    void failureInOneItemDoesNotStopTheJob() {
        when(bookings.lockById(booking.getId())).thenThrow(new IllegalStateException("lock timeout"));

        assertThat(service.reconcile().bookingsCancelled()).isZero();
    }

    private PaymentEntity attempt(Instant sessionExpiresAt) {
        PaymentEntity payment = PaymentEntity.pending(booking, PaymentMethod.PIX);
        payment.setStripeCheckoutSessionId("cs_test_" + payment.getId());
        payment.setSessionExpiresAt(sessionExpiresAt);
        attempts.add(payment);
        return payment;
    }
}
