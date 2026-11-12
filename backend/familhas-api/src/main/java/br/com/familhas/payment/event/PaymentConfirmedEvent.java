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
 * Pagamento aprovado e reserva confirmada. Contrato: docs/events/payment-confirmed.v1.schema.json.
 * Alimenta o e-mail de confirmação e, na Fase 10, a planilha de pagamentos do Admin.
 */
public record PaymentConfirmedEvent(
        UUID eventId,
        UUID paymentId,
        UUID bookingId,
        UUID clientId,
        String email,
        String firstName,
        PaymentMethod method,
        int installments,
        String amount,
        String currency,
        Instant paidAt,
        Instant occurredAt,
        int schemaVersion) implements IntegrationEvent {

    public static final int SCHEMA_VERSION = 1;
    public static final EventType TYPE = new EventType("PaymentConfirmed", "payment.payment-confirmed.v1", "Payment");

    public PaymentConfirmedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static PaymentConfirmedEvent of(PaymentEntity payment, Instant occurredAt) {
        EventRecipient recipient = EventRecipient.of(payment.getBooking().getClient());
        return new PaymentConfirmedEvent(UUID.randomUUID(), payment.getId(), payment.getBooking().getId(),
                payment.getBooking().getClient().getId(), recipient.email(), recipient.firstName(),
                payment.getMethod(), payment.getInstallments(), payment.getAmount().setScale(2).toPlainString(),
                payment.getCurrency(), payment.getPaidAt(), occurredAt, SCHEMA_VERSION);
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
        return "PaymentConfirmedEvent[eventId=" + eventId + ", paymentId=" + paymentId + ", bookingId=" + bookingId
                + "]";
    }
}
