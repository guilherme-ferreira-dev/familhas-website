package br.com.familhas.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Linha da tabela outbox_event (V8). Uso interno da mensageria: nunca sai pela API REST.
 * O id é o eventId do contrato, atribuído pela aplicação (por isso implementa Persistable).
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity extends AuditableEntity implements Persistable<UUID> {

    public static final int LAST_ERROR_MAX_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 150)
    private String routingKey;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Transient
    private boolean isNew;

    public static OutboxEventEntity pending(IntegrationEvent event, String payload, String correlationId,
            Instant now) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = event.eventId();
        entity.aggregateType = event.type().aggregateType();
        entity.aggregateId = event.aggregateId();
        entity.eventType = event.type().name();
        entity.routingKey = event.type().routingKey();
        entity.schemaVersion = event.schemaVersion();
        entity.payload = payload;
        entity.correlationId = correlationId;
        entity.status = OutboxStatus.PENDING;
        entity.attempts = 0;
        entity.availableAt = now;
        entity.isNew = true;
        return entity;
    }

    public void markPublished(Instant now) {
        this.status = OutboxStatus.PUBLISHED;
        this.attempts++;
        this.publishedAt = now;
        this.lastError = null;
    }

    /** Registra a falha e agenda a próxima tentativa, ou desiste depois de maxAttempts. */
    public void markAttemptFailed(String error, Instant nextAttemptAt, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null || error.length() <= LAST_ERROR_MAX_LENGTH
                ? error
                : error.substring(0, LAST_ERROR_MAX_LENGTH);
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        } else {
            this.availableAt = nextAttemptAt;
        }
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
