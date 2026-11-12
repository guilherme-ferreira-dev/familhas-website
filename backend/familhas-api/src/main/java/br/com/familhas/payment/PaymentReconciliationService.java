package br.com.familhas.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingService;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;

/**
 * Reconciliação agendada (ADR 0007, D7). Cobre webhook perdido, Stripe CLI desligado e queda da API.
 *
 * 1. Pagamentos PENDING, PROCESSING ou FAILED sem novidade há algum tempo: consulta a sessão no Stripe e aplica
 *    o mesmo processamento do webhook.
 * 2. Reservas PENDING_PAYMENT vencidas: antes de cancelar, consulta as tentativas ainda vivas no Stripe (um
 *    pagamento do último segundo confirma em vez de virar estorno). Sem pagamento ativo, cancela e devolve os
 *    assentos.
 *
 * Cada item roda na própria transação, com a reserva travada e o estado reavaliado: duas instâncias rodando ao
 * mesmo tempo não causam efeito em dobro, e a falha de um item não para os outros.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);
    private static final Set<PaymentStatus> UNSETTLED = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING,
            PaymentStatus.FAILED);

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentNotificationProcessor processor;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PaymentReconciliationService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
            BookingService bookingService, PaymentNotificationProcessor processor, PaymentGateway gateway,
            PaymentProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.processor = processor;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** @param paymentsChecked tentativas consultadas no Stripe; bookingsCancelled reservas canceladas por prazo */
    public record Result(int paymentsChecked, int bookingsCancelled) {
    }

    public Result reconcile() {
        Instant now = clock.instant();
        int checked = 0;
        if (gateway.isAvailable()) {
            List<UUID> stale = paymentRepository.findStaleIds(UNSETTLED, now.minus(properties.reconciliationStaleAfter()),
                    PageRequest.of(0, properties.reconciliationBatchSize()));
            for (UUID paymentId : stale) {
                if (runIsolated("pagamento", paymentId, () -> reconcilePayment(paymentId))) {
                    checked++;
                }
            }
        }
        int cancelled = 0;
        List<UUID> expired = bookingRepository.findIdsByStatusAndExpiresAtBefore(BookingStatus.PENDING_PAYMENT, now,
                PageRequest.of(0, properties.reconciliationBatchSize()));
        for (UUID bookingId : expired) {
            Boolean done = transactionOrFalse("reserva", bookingId, () -> expireBooking(bookingId));
            if (Boolean.TRUE.equals(done)) {
                cancelled++;
            }
        }
        if (checked > 0 || cancelled > 0) {
            log.info("Reconciliacao: {} pagamento(s) consultado(s), {} reserva(s) cancelada(s) por prazo", checked,
                    cancelled);
        }
        return new Result(checked, cancelled);
    }

    private void reconcilePayment(UUID paymentId) {
        paymentRepository.findById(paymentId)
                .filter(payment -> UNSETTLED.contains(payment.getStatus()))
                .ifPresent(this::refreshFromStripe);
    }

    private boolean expireBooking(UUID bookingId) {
        BookingEntity booking = bookingRepository.lockById(bookingId).orElseThrow();
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || now.isBefore(booking.getExpiresAt())) {
            return false;
        }
        List<PaymentEntity> attempts = paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        if (gateway.isAvailable()) {
            attempts.stream().filter(p -> UNSETTLED.contains(p.getStatus())).forEach(this::refreshFromStripe);
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || attempts.stream().anyMatch(p -> p.isActive(now))) {
            return false;
        }
        booking.cancel(CancellationReason.PAYMENT_EXPIRED, now);
        bookingService.releaseSeats(booking);
        attempts.stream().filter(p -> UNSETTLED.contains(p.getStatus())).forEach(PaymentEntity::markCanceled);
        log.info("Reserva cancelada por prazo: bookingId={}", bookingId);
        return true;
    }

    /** Consulta a sessão no Stripe e aplica o estado como se fosse um webhook. */
    private void refreshFromStripe(PaymentEntity payment) {
        if (payment.getStripeCheckoutSessionId() == null) {
            return;
        }
        gateway.fetchCheckoutSessionState(payment.getStripeCheckoutSessionId())
                .map(state -> new PaymentNotification(null, state.eventType(), state.kind(), payment.getId(),
                        state.checkoutSessionId(), state.paymentIntentId(), state.amount(), state.amountRefunded(),
                        state.currency(), state.failureCode()))
                .ifPresent(processor::apply);
    }

    private boolean runIsolated(String what, UUID id, Runnable action) {
        return Boolean.TRUE.equals(transactionOrFalse(what, id, () -> {
            action.run();
            return true;
        }));
    }

    private Boolean transactionOrFalse(String what, UUID id, java.util.function.Supplier<Boolean> action) {
        try {
            return transactionTemplate.execute(status -> action.get());
        } catch (RuntimeException ex) {
            log.warn("Reconciliacao falhou para {} {}: {}", what, id, ex.getClass().getSimpleName());
            return false;
        }
    }
}
