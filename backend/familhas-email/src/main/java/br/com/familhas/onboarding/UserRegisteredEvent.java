package br.com.familhas.onboarding;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.messaging.ConsumedEvent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Visão do consumidor sobre o contrato docs/events/user-registered.v1.schema.json.
 * As regras de validação espelham o schema; o teste de contrato garante que os dois não divergem.
 */
public record UserRegisteredEvent(
        @NotNull UUID eventId,
        @NotNull UUID userId,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 100) String firstName,
        @NotNull Instant occurredAt,
        @NotNull @Min(1) Integer schemaVersion) implements ConsumedEvent {

    public static final String TYPE = "UserRegistered";

    /** Sem e-mail nem nome: o toString de record poderia acabar em log (LGPD). */
    @Override
    public String toString() {
        return "UserRegisteredEvent[eventId=" + eventId + ", userId=" + userId + ", schemaVersion=" + schemaVersion
                + "]";
    }
}
