package br.com.familhas.payment.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.familhas.messaging.EventRecipient;
import br.com.familhas.messaging.EventType;
import br.com.familhas.messaging.IntegrationEvent;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentMethod;

/**
 * Pagamento recusado (cartão) ou não pago (Pix). A reserva continua aguardando pagamento enquanto houver
 * prazo. Contrato: docs/events/payment-failed.v1.schema.json.
 *
 * @param failureCode código do Stripe (ex.: card_declined, insufficient_funds), sem dados do cartão
 */
public record PaymentFailedEvent(
        UUID eventId,
        UUID paymentId,
        UUID bookingId,
        UUID clientId,
        String email,
        String firstName,
        PaymentMethod method,
        String amount,
        String currency,
        String failureCode,
        Instant occurredAt,
        int schemaVersion) implements IntegrationEvent {

    public static final int SCHEMA_VERSION = 1;
    public static final EventType TYPE = new EventType("PaymentFailed", "payment.payment-failed.v1", "Payment");

    public PaymentFailedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static PaymentFailedEvent of(PaymentEntity payment, Instant occurredAt) {
        EventRecipient recipient = EventRecipient.of(payment.getBooking().getClient());
        return new PaymentFailedEvent(UUID.randomUUID(), payment.getId(), payment.getBooking().getId(),
                payment.getBooking().getClient().getId(), recipient.email(), recipient.firstName(),
                payment.getMethod(), payment.getAmount().setScale(2).toPlainString(), payment.getCurrency(),
                payment.getFailureCode(), occurredAt, SCHEMA_VERSION);
    }

    @Override
    public EventType type() {
        return TYPE;
    }

    @Override
    public String aggregateId() {
        return paymentId.toString();
    }

    @Override
    public String toString() {
        return "PaymentFailedEvent[eventId=" + eventId + ", paymentId=" + paymentId + ", bookingId=" + bookingId
                + "]";
    }
}
