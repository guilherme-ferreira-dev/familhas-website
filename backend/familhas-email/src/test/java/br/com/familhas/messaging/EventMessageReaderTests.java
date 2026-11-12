package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import br.com.familhas.onboarding.UserRegisteredEvent;
import jakarta.validation.Validation;

class EventMessageReaderTests {

    private final EventMessageReader reader = new EventMessageReader(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void readsValidEventAndIgnoresUnknownFields() {
        UserRegisteredEvent event = reader.read(message("""
                {"eventId":"3f1c9a5e-8a51-4d39-9b7e-2f4c1d0a7b11","userId":"c11e0000-0000-4000-8000-0000000000aa",
                 "email":"ana@exemplo.com","firstName":"Ana","occurredAt":"2026-11-11T14:30:00.123456Z",
                 "schemaVersion":2,"campoNovo":"ignorado"}"""), UserRegisteredEvent.class);

        assertThat(event.eventId()).isEqualTo(UUID.fromString("3f1c9a5e-8a51-4d39-9b7e-2f4c1d0a7b11"));
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-11T14:30:00.123456Z"));
        assertThat(event.schemaVersion()).isEqualTo(2);
        assertThat(event.toString()).doesNotContain("ana@exemplo.com").doesNotContain("Ana,");
    }

    @Test
    void missingFieldsAreReportedByNameWithoutValues() {
        assertThatThrownBy(() -> reader.read(message("""
                {"userId":"c11e0000-0000-4000-8000-0000000000aa","email":"segredo-invalido",
                 "occurredAt":"2026-11-11T14:30:00Z"}"""), UserRegisteredEvent.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("eventId")
                .hasMessageContaining("schemaVersion")
                .hasMessageNotContaining("segredo-invalido");
    }

    @Test
    void schemaVersionBelowOneIsInvalid() {
        assertThatThrownBy(() -> reader.read(message("""
                {"eventId":"3f1c9a5e-8a51-4d39-9b7e-2f4c1d0a7b11","userId":"c11e0000-0000-4000-8000-0000000000aa",
                 "email":"ana@exemplo.com","occurredAt":"2026-11-11T14:30:00Z","schemaVersion":0}"""),
                UserRegisteredEvent.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void brokenJsonIsInvalidWithoutEchoingPayload() {
        assertThatThrownBy(() -> reader.read(message("{\"email\":\"vazou@exemplo.com\", quebrado"),
                UserRegisteredEvent.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageNotContaining("vazou@exemplo.com");
    }

    @Test
    void wrongTypeForUuidIsInvalid() {
        assertThatThrownBy(() -> reader.read(message("""
                {"eventId":"nao-e-uuid","userId":"c11e0000-0000-4000-8000-0000000000aa",
                 "email":"ana@exemplo.com","occurredAt":"2026-11-11T14:30:00Z","schemaVersion":1}"""),
                UserRegisteredEvent.class))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void emptyBodyIsInvalid() {
        assertThatThrownBy(() -> reader.read(message(""), UserRegisteredEvent.class))
                .isInstanceOf(InvalidEventException.class);
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
