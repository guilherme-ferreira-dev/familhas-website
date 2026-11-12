package br.com.familhas.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uma tentativa de pagamento da reserva = uma Checkout Session do Stripe (V12; ADR 0007).
 *
 * O id é gerado aqui, antes de chamar o Stripe, porque vai nos metadados da sessão e na idempotency key.
 * O status só muda pelos métodos de transição, que aplicam a máquina de estados de {@link PaymentStatus}
 * e retornam false quando a transição não é permitida (evento fora de ordem ou repetido).
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity extends AuditableEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Id atribuído no código: sem isto, o save faria merge (SELECT antes do INSERT). */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false)
    private Integer installments = 1;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "stripe_checkout_session_id", length = 255)
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Column(name = "checkout_url", length = 2000)
    private String checkoutUrl;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "paid_at")
    @Setter(AccessLevel.NONE)
    private Instant paidAt;

    @Column(name = "failure_code", length = 100)
    @Setter(AccessLevel.NONE)
    private String failureCode;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("number ASC")
    private List<InstallmentEntity> installmentPlan = new ArrayList<>();

    /** Pagamento à vista (uma parcela) do total da reserva. */
    public static PaymentEntity pending(BookingEntity booking, PaymentMethod method) {
        PaymentEntity payment = new PaymentEntity();
        payment.id = UUID.randomUUID();
        payment.booking = booking;
        payment.method = method;
        payment.amount = booking.getTotalAmount();
        payment.currency = booking.getCurrency();
        payment.idempotencyKey = "checkout-session:" + payment.id;
        InstallmentEntity single = new InstallmentEntity();
        single.setPayment(payment);
        single.setNumber(1);
        single.setAmount(payment.amount);
        single.setCurrency(payment.currency);
        payment.installmentPlan.add(single);
        return payment;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    /** Idempotency key de outra operação no Stripe sobre esta mesma tentativa (ex.: "expire", "refund"). */
    public String idempotencyKeyFor(String operation) {
        return operation + ":" + id;
    }

    /** Sessão de checkout ainda utilizável pelo cliente (aberta, ou com cartão recusado mas dentro do prazo). */
    public boolean hasOpenSession(Instant now) {
        return (status == PaymentStatus.PENDING || status == PaymentStatus.FAILED)
                && sessionExpiresAt != null && now.isBefore(sessionExpiresAt);
    }

    /** Pagamento que ainda pode virar SUCCEEDED: segura a reserva e os assentos. */
    public boolean isActive(Instant now) {
        return status == PaymentStatus.PROCESSING || hasOpenSession(now);
    }

    public boolean markProcessing() {
        return moveTo(PaymentStatus.PROCESSING);
    }

    public boolean markSucceeded(Instant now) {
        if (!moveTo(PaymentStatus.SUCCEEDED)) {
            return false;
        }
        paidAt = now;
        failureCode = null;
        return true;
    }

    public boolean markFailed(String code) {
        if (!moveTo(PaymentStatus.FAILED)) {
            return false;
        }
        failureCode = code == null ? null : code.length() > 100 ? code.substring(0, 100) : code;
        return true;
    }

    public boolean markCanceled() {
        return moveTo(PaymentStatus.CANCELED);
    }

    /**
     * Estorno informado pelo Stripe com o total estornado até agora (charge.amount_refunded).
     * Total: REFUNDED. Parcial: PARTIALLY_REFUNDED. Retorna false se nada mudou.
     */
    public boolean applyRefund(BigDecimal totalRefunded) {
        if (totalRefunded.compareTo(refundedAmount) <= 0) {
            return false;
        }
        PaymentStatus target = totalRefunded.compareTo(amount) >= 0 ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        if (!moveTo(target)) {
            return false;
        }
        refundedAmount = totalRefunded.min(amount);
        return true;
    }

    private boolean moveTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        status = target;
        return true;
    }
}
