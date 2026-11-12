package br.com.familhas.messaging;

import java.time.Instant;
import java.util.UUID;

/** Evento mínimo só para testes da infraestrutura de mensageria (prova que a base é genérica). */
record TestEvent(UUID eventId, String aggregateId, Instant occurredAt, int schemaVersion, String routingKey)
        implements IntegrationEvent {

    static TestEvent create(String routingKey) {
        return new TestEvent(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now(), 1, routingKey);
    }

    @Override
    public EventType type() {
        return new EventType("TestEvent", routingKey, "Test");
    }
}
