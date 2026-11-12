package br.com.familhas.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * Campos-base de todo evento consumido (docs/events/README.md). UserRegistered implementa agora;
 * BookingConfirmed e PaymentConfirmed seguem o mesmo molde nas próximas fases.
 */
public interface ConsumedEvent {

    UUID eventId();

    Instant occurredAt();

    Integer schemaVersion();
}
