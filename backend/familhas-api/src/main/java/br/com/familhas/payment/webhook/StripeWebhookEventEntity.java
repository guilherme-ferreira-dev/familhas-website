package br.com.familhas.payment.webhook;

import org.springframework.data.domain.Persistable;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Evento do Stripe já processado (V12). A chave primária é o event.id: gravar duas vezes o mesmo evento
 * viola a PK, e a transação que faria o efeito em dobro é desfeita (ADR 0007, D6). O payload não é guardado.
 */
@Entity
@Table(name = "stripe_webhook_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripeWebhookEventEntity extends AuditableEntity implements Persistable<String> {

    public enum Outcome {
        /** Mudou (ou confirmou) o estado de um pagamento da Familhas. */
        PROCESSED,
        /** Tipo não tratado ou objeto que não é da Familhas. */
        IGNORED
    }

    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Outcome outcome;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public StripeWebhookEventEntity(String eventId, String eventType, Outcome outcome) {
        this.eventId = eventId;
        this.eventType = eventType.length() > 100 ? eventType.substring(0, 100) : eventType;
        this.outcome = outcome;
    }

    @Override
    public String getId() {
        return eventId;
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
}
