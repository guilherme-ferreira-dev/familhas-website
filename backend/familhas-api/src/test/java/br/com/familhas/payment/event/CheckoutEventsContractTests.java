package br.com.familhas.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.event.BookingCreatedEvent;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.messaging.EventSerializer;
import br.com.familhas.messaging.IntegrationEvent;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentMethod;

/**
 * O JSON publicado pela API para reserva e pagamento segue os contratos versionados em docs/events.
 * O familhas-email tem o teste espelhado, contra os mesmos arquivos.
 */
class CheckoutEventsContractTests {

    private static final Instant NOW = Instant.parse("2026-11-12T15:00:00.123456Z");

    private final EventSerializer serializer = new EventSerializer();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bookingCreatedMatchesSchema() throws Exception {
        BookingCreatedEvent event = BookingCreatedEvent.of(booking(), NOW);

        JsonNode json = json(event);

        assertThat(validate("booking-created", json)).isEmpty();
        assertThat(json.get("totalAmount").asText()).isEqualTo("7830.10");
        assertThat(json.get("firstName").asText()).isEqualTo("Ana");
        assertThat(event.type().routingKey()).isEqualTo("booking.booking-created.v1");
    }

    @Test
    void paymentConfirmedMatchesSchema() throws Exception {
        PaymentEntity payment = PaymentEntity.pending(booking(), PaymentMethod.PIX);
        payment.markSucceeded(NOW);

        JsonNode json = json(PaymentConfirmedEvent.of(payment, NOW));

        assertThat(validate("payment-confirmed", json)).isEmpty();
        assertThat(json.get("method").asText()).isEqualTo("PIX");
        assertThat(json.get("installments").asInt()).isEqualTo(1);
        assertThat(json.get("amount").asText()).isEqualTo("7830.10");
    }

    @Test
    void paymentFailedMatchesSchema() throws Exception {
        PaymentEntity payment = PaymentEntity.pending(booking(), PaymentMethod.CREDIT_CARD);
        payment.markFailed("insufficient_funds");

        JsonNode json = json(PaymentFailedEvent.of(payment, NOW));

        assertThat(validate("payment-failed", json)).isEmpty();
        assertThat(json.get("failureCode").asText()).isEqualTo("insufficient_funds");
    }

    @Test
    void toStringNeverCarriesPersonalData() {
        PaymentEntity payment = PaymentEntity.pending(booking(), PaymentMethod.PIX);

        assertThat(BookingCreatedEvent.of(booking(), NOW).toString()).doesNotContain("ana@").doesNotContain("Ana");
        assertThat(PaymentConfirmedEvent.of(payment, NOW).toString()).doesNotContain("ana@");
        assertThat(PaymentFailedEvent.of(payment, NOW).toString()).doesNotContain("ana@");
    }

    @Test
    void amountWithLettersOrMissingDecimalsIsRejectedBySchema() throws Exception {
        JsonNode example = mapper.readTree(Path.of("../../docs/events/examples/payment-confirmed.v1.json").toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) example).put("amount", "7830.1");

        assertThat(validate("payment-confirmed", example)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "booking-created", "payment-confirmed", "payment-failed" })
    void documentedExamplesAreValid(String name) throws Exception {
        JsonNode example = mapper.readTree(Path.of("../../docs/events/examples/" + name + ".v1.json").toFile());

        assertThat(validate(name, example)).isEmpty();
    }

    private JsonNode json(IntegrationEvent event) throws Exception {
        return mapper.readTree(serializer.toJson(event));
    }

    private static BookingEntity booking() {
        ClientEntity client = new ClientEntity();
        client.setId(UUID.fromString("c11e0000-0000-4000-8000-0000000000aa"));
        client.setEmail("ana@exemplo.com");
        client.setName("  Ana   Souza ");
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.fromString("8d0e4c7a-31b2-4f0f-9c64-5b0a2e7d4f10"));
        booking.setClient(client);
        booking.setTotalAmount(new BigDecimal("7830.1"));
        booking.setCurrency("BRL");
        booking.setExpiresAt(NOW.plusSeconds(1800));
        return booking;
    }

    private static Set<ValidationMessage> validate(String name, JsonNode json) throws Exception {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        try (InputStream in = Files.newInputStream(Path.of("../../docs/events/" + name + ".v1.schema.json"))) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(in, config);
            return schema.validate(json);
        }
    }
}
