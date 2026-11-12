package br.com.familhas.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Base de todo evento de integração publicado pela API (UserRegistered agora; BookingConfirmed e
 * PaymentConfirmed nas próximas fases). Os campos-base e as regras de versão estão em docs/events/README.md.
 *
 * Um novo evento é um record que implementa esta interface, tem um {@link EventType} fixo e um JSON Schema
 * em docs/events. O OutboxService grava qualquer IntegrationEvent sem código específico.
 */
public interface IntegrationEvent {

    UUID eventId();

    Instant occurredAt();

    int schemaVersion();

    /** Tipo e routing key. Não faz parte do corpo JSON. */
    @JsonIgnore
    EventType type();

    /** Id do agregado de origem, para rastrear o evento no outbox. Não faz parte do corpo JSON. */
    @JsonIgnore
    String aggregateId();
}
