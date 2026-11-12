package br.com.familhas.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingService;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;
import jakarta.persistence.EntityManager;

/**
 * Aplica ao domínio um fato do Stripe (webhook ou reconciliação; ADR 0007, D4 e D6).
 *
 * - Trava a reserva antes de mexer no pagamento: mesma ordem do checkout, sem deadlock.
 * - Transição não permitida (evento fora de ordem, repetido com outro id) não muda nada.
 * - Pagamento aprovado confirma a reserva. Se a reserva já foi paga por outra tentativa, ou já foi cancelada,
 *   o valor é estornado automaticamente: o cliente nunca fica cobrado sem reserva.
 * - Estorno total cancela a reserva e devolve os assentos, a menos que outra tentativa paga a sustente.
 */
@Service
public class PaymentNotificationProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationProcessor.class);
    private static final Set<PaymentStatus> PAID = EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGateway gateway;
    private final EntityManager entityManager;
    private final Clock clock;

    public PaymentNotificationProcessor(PaymentRepository paymentRepository, BookingRepository bookingRepository,
            BookingService bookingService, PaymentGateway gateway, EntityManager entityManager, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.gateway = gateway;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** @return true se o fato era de um pagamento da Familhas (mudando ou não o estado) */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(PaymentNotification notification) {
        if (notification.kind() == PaymentNotification.Kind.IGNORED) {
            return false;
        }
        Optional<PaymentEntity> found = locate(notification);
        if (found.isEmpty()) {
            log.info("Fato do Stripe sem pagamento correspondente: eventId={} type={}", notification.eventId(),
                    notification.eventType());
            return false;
        }
        BookingEntity booking = bookingRepository.lockById(found.get().getBooking().getId()).orElseThrow();
        PaymentEntity payment = found.get();
        entityManager.refresh(payment);
        if (payment.getStripePaymentIntentId() == null && notification.paymentIntentId() != null) {
            payment.setStripePaymentIntentId(notification.paymentIntentId());
        }

        Instant now = clock.instant();
        PaymentStatus before = payment.getStatus();
        switch (notification.kind()) {
            case SUCCEEDED -> succeeded(payment, booking, notification, now);
            case PROCESSING -> payment.markProcessing();
            case FAILED -> payment.markFailed(notification.failureCode());
            case SESSION_EXPIRED -> payment.markCanceled();
            case REFUNDED -> refunded(payment, booking, notification, now);
            case IGNORED -> { }
        }
        log.info("Pagamento atualizado: paymentId={} bookingId={} {} -> {} (eventId={} type={})", payment.getId(),
                booking.getId(), before, payment.getStatus(), notification.eventId(), notification.eventType());
        return true;
    }

    private void succeeded(PaymentEntity payment, BookingEntity booking, PaymentNotification notification,
            Instant now) {
        if (!sameAmount(payment, notification)) {
            log.error("Valor pago diverge do pagamento: paymentId={} eventId={}; nada foi confirmado, conferir no "
                    + "Dashboard", payment.getId(), notification.eventId());
            return;
        }
        if (!payment.markSucceeded(now)) {
            return;
        }
        if (booking.confirm(now)) {
            expireOtherOpenSessions(payment, booking, now);
            return;
        }
        String reason = booking.getStatus() == BookingStatus.CANCELLED ? "reserva cancelada"
                : "reserva ja paga por outra tentativa";
        refundAutomatically(payment, reason);
    }

    private void refunded(PaymentEntity payment, BookingEntity booking, PaymentNotification notification,
            Instant now) {
        if (notification.amountRefunded() == null || !payment.applyRefund(notification.amountRefunded())) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.REFUNDED || anotherPaidAttempt(payment, booking)) {
            return;
        }
        if (booking.cancel(CancellationReason.REFUNDED, now)) {
            bookingService.releaseSeats(booking);
            log.info("Reserva cancelada por estorno total: bookingId={} paymentId={}", booking.getId(),
                    payment.getId());
        }
    }

    /** O pagamento confirmou a reserva: as outras sessões abertas não podem mais cobrar. */
    private void expireOtherOpenSessions(PaymentEntity paid, BookingEntity booking, Instant now) {
        for (PaymentEntity other : paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId())) {
            if (other.getId().equals(paid.getId()) || !other.hasOpenSession(now)) {
                continue;
            }
            try {
                gateway.expireCheckoutSession(other.getStripeCheckoutSessionId(), other.idempotencyKeyFor("expire"));
                other.markCanceled();
            } catch (PaymentGatewayException ex) {
                log.warn("Nao foi possivel expirar a sessao de outra tentativa: paymentId={}", other.getId());
            }
        }
    }

    private void refundAutomatically(PaymentEntity payment, String reason) {
        if (payment.getStripePaymentIntentId() == null) {
            log.error("Pagamento aprovado sem PaymentIntent para estornar ({}): paymentId={}; estornar no Dashboard",
                    reason, payment.getId());
            return;
        }
        log.warn("Pagamento aprovado com {}: estornando paymentId={}", reason, payment.getId());
        gateway.refund(payment.getStripePaymentIntentId(), payment.idempotencyKeyFor("refund"));
    }

    private boolean anotherPaidAttempt(PaymentEntity payment, BookingEntity booking) {
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .anyMatch(p -> !p.getId().equals(payment.getId()) && PAID.contains(p.getStatus()));
    }

    private static boolean sameAmount(PaymentEntity payment, PaymentNotification notification) {
        if (notification.amount() == null) {
            return true;
        }
        return notification.amount().compareTo(payment.getAmount()) == 0
                && (notification.currency() == null || notification.currency().equals(payment.getCurrency()));
    }

    private Optional<PaymentEntity> locate(PaymentNotification notification) {
        if (notification.paymentId() != null) {
            Optional<PaymentEntity> byId = paymentRepository.findById(notification.paymentId());
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (notification.checkoutSessionId() != null) {
            Optional<PaymentEntity> bySession = paymentRepository
                    .findByStripeCheckoutSessionId(notification.checkoutSessionId());
            if (bySession.isPresent()) {
                return bySession;
            }
        }
        if (notification.paymentIntentId() != null) {
            return paymentRepository.findFirstByStripePaymentIntentId(notification.paymentIntentId());
        }
        return Optional.empty();
    }
}
