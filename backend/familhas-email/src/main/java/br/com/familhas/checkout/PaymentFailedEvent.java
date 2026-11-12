package br.com.familhas.checkout;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.messaging.ConsumedEvent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Visão do consumidor sobre docs/events/payment-failed.v1.schema.json. */
public record PaymentFailedEvent(
        @NotNull UUID eventId,
        @NotNull UUID paymentId,
        @NotNull UUID bookingId,
        @NotNull UUID clientId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 100) String firstName,
        @NotNull @Pattern(regexp = CheckoutEmails.METHOD_PATTERN) String method,
        @NotNull @Pattern(regexp = CheckoutEmails.AMOUNT_PATTERN) String amount,
        @NotNull @Pattern(regexp = CheckoutEmails.CURRENCY_PATTERN) String currency,
        @Size(max = 100) String failureCode,
        @NotNull Instant occurredAt,
        @NotNull @Min(1) Integer schemaVersion) implements ConsumedEvent {

    public static final String TYPE = "PaymentFailed";

    @Override
    public String toString() {
        return "PaymentFailedEvent[eventId=" + eventId + ", paymentId=" + paymentId + "]";
    }
}
