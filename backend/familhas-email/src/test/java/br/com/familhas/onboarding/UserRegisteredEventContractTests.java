package br.com.familhas.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import br.com.familhas.messaging.EventMessageReader;
import br.com.familhas.messaging.InvalidEventException;
import jakarta.validation.Validation;

/**
 * Espelho do teste de contrato da familhas-api: o consumidor aceita o que o schema versionado aceita
 * e recusa o que ele recusa.
 */
class UserRegisteredEventContractTests {

    private static final Path SCHEMA = Path.of("../../docs/events/user-registered.v1.schema.json");
    private static final Path EXAMPLE = Path.of("../../docs/events/examples/user-registered.v1.json");

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventMessageReader reader = new EventMessageReader(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void documentedExampleIsValidForSchemaAndForConsumer() throws Exception {
        byte[] body = Files.readAllBytes(EXAMPLE);

        assertThat(validate(mapper.readTree(body))).isEmpty();
        UserRegisteredEvent event = reader.read(new Message(body, new MessageProperties()), UserRegisteredEvent.class);
        assertThat(event.firstName()).isEqualTo("Ana");
        assertThat(event.schemaVersion()).isEqualTo(1);
    }

    @Test
    void payloadWithoutFirstNameIsAcceptedByBoth() throws Exception {
        String json = """
                {"eventId":"3f1c9a5e-8a51-4d39-9b7e-2f4c1d0a7b11","userId":"c11e0000-0000-4000-8000-0000000000aa",
                 "email":"ana@exemplo.com","firstName":null,"occurredAt":"2026-11-11T14:30:00Z","schemaVersion":1}""";

        assertThat(validate(mapper.readTree(json))).isEmpty();
        assertThat(reader.read(new Message(json.getBytes(), new MessageProperties()), UserRegisteredEvent.class)
                .firstName()).isNull();
    }

    @Test
    void payloadWithoutEmailIsRejectedByBoth() throws Exception {
        String json = """
                {"eventId":"3f1c9a5e-8a51-4d39-9b7e-2f4c1d0a7b11","userId":"c11e0000-0000-4000-8000-0000000000aa",
                 "occurredAt":"2026-11-11T14:30:00Z","schemaVersion":1}""";

        assertThat(validate(mapper.readTree(json))).isNotEmpty();
        assertThatThrownBy(() -> reader.read(new Message(json.getBytes(), new MessageProperties()),
                UserRegisteredEvent.class)).isInstanceOf(InvalidEventException.class);
    }

    private static Set<ValidationMessage> validate(JsonNode json) throws Exception {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream in = Files.newInputStream(SCHEMA)) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
            return schema.validate(json);
        }
    }
}
