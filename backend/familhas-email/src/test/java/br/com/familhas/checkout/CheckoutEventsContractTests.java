package br.com.familhas.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import br.com.familhas.messaging.ConsumedEvent;
import br.com.familhas.messaging.EventMessageReader;
import br.com.familhas.messaging.InvalidEventException;
import jakarta.validation.Validation;

/**
 * Espelho do teste de contrato da familhas-api para reserva e pagamento: o consumidor aceita o que o schema
 * versionado aceita e recusa o que ele recusa.
 */
class CheckoutEventsContractTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventMessageReader reader = new EventMessageReader(
            Validation.buildDefaultValidatorFactory().getValidator());

    @ParameterizedTest
    @CsvSource({
            "booking-created, br.com.familhas.checkout.BookingCreatedEvent",
            "payment-confirmed, br.com.familhas.checkout.PaymentConfirmedEvent",
            "payment-failed, br.com.familhas.checkout.PaymentFailedEvent" })
    void documentedExampleIsValidForSchemaAndForConsumer(String name, Class<? extends ConsumedEvent> type)
            throws Exception {
        byte[] body = Files.readAllBytes(example(name));

        assertThat(validate(name, mapper.readTree(body))).isEmpty();
        assertThat(reader.read(message(body), type).schemaVersion()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "payment-confirmed, amount, 7830.1",
            "payment-confirmed, amount, 7830,16",
            "payment-confirmed, method, BOLETO",
            "payment-failed, currency, brl",
            "booking-created, totalAmount, abc" })
    void invalidFieldIsRejectedByBoth(String name, String field, String value) throws Exception {
        ObjectNode json = (ObjectNode) mapper.readTree(Files.readAllBytes(example(name)));
        json.put(field, value);
        byte[] body = mapper.writeValueAsBytes(json);

        assertThat(validate(name, json)).isNotEmpty();
        assertThatThrownBy(() -> reader.read(message(body), typeOf(name))).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void missingEmailIsRejectedByBoth() throws Exception {
        ObjectNode json = (ObjectNode) mapper.readTree(Files.readAllBytes(example("payment-failed")));
        json.remove("email");

        assertThat(validate("payment-failed", json)).isNotEmpty();
        assertThatThrownBy(() -> reader.read(message(mapper.writeValueAsBytes(json)), PaymentFailedEvent.class))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void failureCodeIsOptionalForBoth() throws Exception {
        ObjectNode json = (ObjectNode) mapper.readTree(Files.readAllBytes(example("payment-failed")));
        json.putNull("failureCode");

        assertThat(validate("payment-failed", json)).isEmpty();
        assertThat(reader.read(message(mapper.writeValueAsBytes(json)), PaymentFailedEvent.class).failureCode())
                .isNull();
    }

    private static Class<? extends ConsumedEvent> typeOf(String name) {
        return switch (name) {
            case "booking-created" -> BookingCreatedEvent.class;
            case "payment-confirmed" -> PaymentConfirmedEvent.class;
            default -> PaymentFailedEvent.class;
        };
    }

    private static Path example(String name) {
        return Path.of("../../docs/events/examples/" + name + ".v1.json");
    }

    private static Message message(byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private static Set<ValidationMessage> validate(String name, JsonNode json) throws Exception {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream in = Files.newInputStream(Path.of("../../docs/events/" + name + ".v1.schema.json"))) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
            return schema.validate(json);
        }
    }
}
