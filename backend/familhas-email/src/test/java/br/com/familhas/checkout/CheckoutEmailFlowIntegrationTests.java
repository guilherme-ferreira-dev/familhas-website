package br.com.familhas.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.support.MailHogClient.ReceivedEmail;
import br.com.familhas.support.MessagingIntegrationTestSupport;

/** Fluxo real dos e-mails de reserva e pagamento: RabbitMQ → listener → Oracle (idempotência) → MailHog. */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutEmailFlowIntegrationTests extends MessagingIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @BeforeEach
    void purgeCheckoutDeadLetterQueues() {
        amqpAdmin.purgeQueue(MessagingTopology.BOOKING_CREATED_DLQ, false);
        amqpAdmin.purgeQueue(MessagingTopology.PAYMENT_CONFIRMED_DLQ, false);
        amqpAdmin.purgeQueue(MessagingTopology.PAYMENT_FAILED_DLQ, false);
    }

    @Test
    void eachEventSendsItsEmailAndLogsCarryNoPersonalData(CapturedOutput output) {
        String email = uniqueEmail("caio");

        send(MessagingTopology.BOOKING_CREATED_ROUTING_KEY, "BookingCreated", """
                {"eventId":"%s","bookingId":"%s","clientId":"%s","email":"%s","firstName":"Caio",
                 "totalAmount":"7830.16","currency":"BRL","expiresAt":"%s","occurredAt":"%s","schemaVersion":1}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), email,
                        Instant.now().plusSeconds(1800), Instant.now()));
        send(MessagingTopology.PAYMENT_FAILED_ROUTING_KEY, "PaymentFailed", paymentJson(UUID.randomUUID(), email,
                "\"failureCode\":\"card_declined\""));
        send(MessagingTopology.PAYMENT_CONFIRMED_ROUTING_KEY, "PaymentConfirmed", paymentJson(UUID.randomUUID(),
                email, "\"installments\":1,\"paidAt\":\"" + Instant.now() + "\""));

        await().atMost(TIMEOUT).until(() -> mailHog.messagesTo(email).size() == 3);
        assertThat(mailHog.messagesTo(email)).extracting(ReceivedEmail::subject).containsExactlyInAnyOrder(
                CheckoutEmails.BOOKING_CREATED_SUBJECT, CheckoutEmails.PAYMENT_FAILED_SUBJECT,
                CheckoutEmails.PAYMENT_CONFIRMED_SUBJECT);
        assertThat(output.getAll()).doesNotContain(email).doesNotContain("Caio");
    }

    @Test
    void redeliveredPaymentConfirmedDoesNotSendTheEmailAgain() {
        String email = uniqueEmail("dup");
        UUID eventId = UUID.randomUUID();
        String json = paymentJson(eventId, email, "\"installments\":1,\"paidAt\":\"" + Instant.now() + "\"");
        String sentinel = uniqueEmail("sentinela");

        send(MessagingTopology.PAYMENT_CONFIRMED_ROUTING_KEY, "PaymentConfirmed", json);
        send(MessagingTopology.PAYMENT_CONFIRMED_ROUTING_KEY, "PaymentConfirmed", json);
        // Fila FIFO com um consumidor: quando a sentinela chega, as duas cópias já foram tratadas.
        send(MessagingTopology.PAYMENT_CONFIRMED_ROUTING_KEY, "PaymentConfirmed", paymentJson(UUID.randomUUID(),
                sentinel, "\"installments\":1,\"paidAt\":\"" + Instant.now() + "\""));

        await().atMost(TIMEOUT).until(() -> mailHog.messagesTo(sentinel).size() == 1);
        assertThat(mailHog.messagesTo(email)).hasSize(1);
    }

    @Test
    void invalidAmountGoesToTheDeadLetterQueue() {
        UUID eventId = UUID.randomUUID();
        String json = paymentJson(eventId, uniqueEmail("dlq"), "\"failureCode\":null").replace("\"100.00\"", "100");

        send(MessagingTopology.PAYMENT_FAILED_ROUTING_KEY, "PaymentFailed", json);

        Message dead = rabbitTemplate.receive(MessagingTopology.PAYMENT_FAILED_DLQ, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
    }

    private static String paymentJson(UUID eventId, String email, String extra) {
        return """
                {"eventId":"%s","paymentId":"%s","bookingId":"%s","clientId":"%s","email":"%s","firstName":"Caio",
                 "method":"CREDIT_CARD","amount":"100.00","currency":"BRL",%s,"occurredAt":"%s","schemaVersion":1}"""
                .formatted(eventId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), email, extra,
                        Instant.now());
    }

    private void send(String routingKey, String type, String json) {
        String eventId = json.substring(json.indexOf("\"eventId\":\"") + 11, json.indexOf("\"eventId\":\"") + 47);
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setMessageId(eventId);
        props.setCorrelationId("corr-" + eventId);
        props.setType(type);
        rabbitTemplate.send(MessagingTopology.EVENTS_EXCHANGE, routingKey,
                new Message(json.getBytes(StandardCharsets.UTF_8), props));
    }
}
