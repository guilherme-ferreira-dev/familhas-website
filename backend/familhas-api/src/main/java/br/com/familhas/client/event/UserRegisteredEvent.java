package br.com.familhas.client.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.familhas.messaging.EventType;
import br.com.familhas.messaging.IntegrationEvent;

/**
 * Cliente criado no primeiro login. Contrato: docs/events/user-registered.v1.schema.json.
 *
 * @param userId    id do Client local (não é o sub do Keycloak)
 * @param email     e-mail verificado, em minúsculas (dado pessoal)
 * @param firstName primeiro nome, pode ser nulo (dado pessoal)
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String firstName,
        Instant occurredAt,
        int schemaVersion) implements IntegrationEvent {

    public static final int SCHEMA_VERSION = 1;
    public static final int FIRST_NAME_MAX_LENGTH = 100;
    public static final EventType TYPE = new EventType("UserRegistered", "client.user-registered.v1", "Client");

    public UserRegisteredEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static UserRegisteredEvent of(UUID userId, String email, String firstName, Instant occurredAt) {
        return new UserRegisteredEvent(UUID.randomUUID(), userId, email, normalizeFirstName(firstName), occurredAt,
                SCHEMA_VERSION);
    }

    @Override
    public EventType type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return userId.toString();
    }

    /** Sem e-mail nem nome: o toString de record poderia acabar em log (LGPD). */
    @Override
    public String toString() {
        return "UserRegisteredEvent[eventId=" + eventId + ", userId=" + userId + ", schemaVersion=" + schemaVersion
                + "]";
    }

    private static String normalizeFirstName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            return null;
        }
        String trimmed = firstName.trim();
        return trimmed.length() > FIRST_NAME_MAX_LENGTH ? trimmed.substring(0, FIRST_NAME_MAX_LENGTH) : trimmed;
    }
}
