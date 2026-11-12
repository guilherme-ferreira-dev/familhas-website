package br.com.familhas.client.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import br.com.familhas.messaging.EventSerializer;

/**
 * Garante que o JSON publicado pela API segue o contrato versionado em docs/events.
 * O familhas-email tem o teste espelhado, contra o mesmo arquivo.
 */
class UserRegisteredEventContractTests {

    private static final Path SCHEMA = Path.of("../../docs/events/user-registered.v1.schema.json");

    private final EventSerializer serializer = new EventSerializer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializedEventMatchesVersionedSchema() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.of(UUID.randomUUID(), "ana@familhas.com", "Ana",
                Instant.parse("2026-11-11T14:30:00.123456Z"));

        JsonNode json = mapper.readTree(serializer.toJson(event));

        assertThat(validate(json)).isEmpty();
        assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("eventId", "userId", "email", "firstName", "occurredAt", "schemaVersion");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-11-11T14:30:00.123456Z");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void eventWithoutFirstNameIsStillValid() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.of(UUID.randomUUID(), "ana@familhas.com", "  ",
                Instant.now());

        JsonNode json = mapper.readTree(serializer.toJson(event));

        assertThat(event.firstName()).isNull();
        assertThat(validate(json)).isEmpty();
    }

    @Test
    void schemaRejectsPayloadWithoutEventId() throws Exception {
        JsonNode json = mapper.readTree("""
                {"userId":"c11e0000-0000-4000-8000-0000000000aa","email":"ana@familhas.com",
                 "occurredAt":"2026-11-11T14:30:00Z","schemaVersion":1}""");

        assertThat(validate(json)).isNotEmpty();
    }

    @Test
    void documentedExampleIsValid() throws Exception {
        JsonNode example = mapper.readTree(Path.of("../../docs/events/examples/user-registered.v1.json").toFile());

        assertThat(validate(example)).isEmpty();
    }

    @Test
    void longFirstNameIsTruncatedAndToStringHidesPersonalData() {
        UserRegisteredEvent event = UserRegisteredEvent.of(UUID.randomUUID(), "ana@familhas.com", "A".repeat(150),
                Instant.now());

        assertThat(event.firstName()).hasSize(UserRegisteredEvent.FIRST_NAME_MAX_LENGTH);
        assertThat(event.toString()).doesNotContain("ana@familhas.com").doesNotContain("AAAA");
        assertThat(event.type().routingKey()).isEqualTo("client.user-registered.v1");
        assertThat(event.aggregateId()).isEqualTo(event.userId().toString());
    }

    private static Set<ValidationMessage> validate(JsonNode json) throws Exception {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream in = Files.newInputStream(SCHEMA)) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
            return schema.validate(json);
        }
    }
}
