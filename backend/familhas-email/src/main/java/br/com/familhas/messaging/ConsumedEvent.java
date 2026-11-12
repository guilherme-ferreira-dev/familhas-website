package br.com.familhas.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Campos-base de todo evento consumido (docs/events/README.md): UserRegistered, BookingCreated,
 * PaymentConfirmed e PaymentFailed.
 */
public interface ConsumedEvent {

    UUID eventId();

    Instant occurredAt();

    Integer schemaVersion();
}
