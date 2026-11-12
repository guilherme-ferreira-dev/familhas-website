package br.com.familhas.booking.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.messaging.EventRecipient;
import br.com.familhas.messaging.EventType;
import br.com.familhas.messaging.IntegrationEvent;

/**
 * Reserva criada, aguardando pagamento. Contrato: docs/events/booking-created.v1.schema.json.
 *
 * @param totalAmount decimal com 2 casas em texto ("7830.16"): sem perda de precisão em nenhum consumidor
 * @param expiresAt   prazo para pagar
 */
public record BookingCreatedEvent(
        UUID eventId,
        UUID bookingId,
        UUID clientId,
        String email,
        String firstName,
        String totalAmount,
        String currency,
        Instant expiresAt,
        Instant occurredAt,
        int schemaVersion) implements IntegrationEvent {

    public static final int SCHEMA_VERSION = 1;
    public static final EventType TYPE = new EventType("BookingCreated", "booking.booking-created.v1", "Booking");

    public BookingCreatedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static BookingCreatedEvent of(BookingEntity booking, Instant occurredAt) {
        EventRecipient recipient = EventRecipient.of(booking.getClient());
        return new BookingCreatedEvent(UUID.randomUUID(), booking.getId(), booking.getClient().getId(),
                recipient.email(), recipient.firstName(), booking.getTotalAmount().setScale(2).toPlainString(),
                booking.getCurrency(), booking.getExpiresAt(), occurredAt, SCHEMA_VERSION);
    }

    @Override
    public EventType type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return bookingId.toString();
    }

    /** Sem e-mail nem nome (LGPD). */
    @Override
    public String toString() {
        return "BookingCreatedEvent[eventId=" + eventId + ", bookingId=" + bookingId + "]";
    }
}
