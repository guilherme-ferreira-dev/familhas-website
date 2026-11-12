package br.com.familhas.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.messaging.ProcessedEventRepository;
import br.com.familhas.support.MailHogClient.ReceivedEmail;
import br.com.familhas.support.MessagingIntegrationTestSupport;

/** Fluxo real: RabbitMQ → listener → Oracle (idempotência) → SMTP do MailHog. */
@ExtendWith(OutputCaptureExtension.class)
class UserRegisteredFlowIntegrationTests extends MessagingIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired private ProcessedEventRepository processedEventRepository;

    @Test
    void welcomeEmailArrivesInMailHogAndLogsCarryNoPersonalData(CapturedOutput output) {
        UUID eventId = UUID.randomUUID();
        String email = uniqueEmail("bia");

        publish(eventId, userRegisteredJson(eventId, email, "Beatriz"));

        await().atMost(TIMEOUT).until(() -> mailHog.messagesTo(email).size() == 1);
        ReceivedEmail received = mailHog.messagesTo(email).get(0);
        assertThat(received.subject()).isEqualTo("Boas-vindas à Familhas");
        assertThat(received.body()).startsWith("Olá, Beatriz!").contains("Sua conta na Familhas está pronta.");
        await().atMost(TIMEOUT).until(() -> processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER));

        assertThat(output.getAll()).contains(eventId.toString()).doesNotContain(email).doesNotContain("Beatriz");
    }

    @Test
    void redeliveredEventIdDoesNotSendTheEmailAgain() {
        UUID eventId = UUID.randomUUID();
        String email = uniqueEmail("dup");
        String json = userRegisteredJson(eventId, email, "Duda");
        UUID sentinelId = UUID.randomUUID();
        String sentinelEmail = uniqueEmail("sentinela");

        publish(eventId, json);
        publish(eventId, json);
        publish(eventId, json);
        // Fila FIFO com um consumidor: quando a sentinela chega, as três cópias já foram tratadas.
        publish(sentinelId, userRegisteredJson(sentinelId, sentinelEmail, "Sentinela"));

        await().atMost(TIMEOUT).until(() -> mailHog.messagesTo(sentinelEmail).size() == 1);
        assertThat(mailHog.messagesTo(email)).hasSize(1);
        assertThat(rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, 500)).isNull();
    }

    @Test
    void invalidPayloadGoesToDeadLetterQueueWithoutEmail(CapturedOutput output) {
        UUID eventId = UUID.randomUUID();
        String invalidEmail = "invalido-" + UUID.randomUUID();

        publish(eventId, userRegisteredJson(eventId, invalidEmail, "Iva"));

        Message dead = rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        List<?> xDeath = dead.getMessageProperties().getHeader("x-death");
        assertThat(xDeath).isNotEmpty();
        assertThat(processedEventRepository.exists(eventId, UserRegisteredListener.CONSUMER)).isFalse();
        assertThat(output.getAll()).contains("InvalidEventException").doesNotContain(invalidEmail);
    }

    @Test
    void malformedJsonGoesToDeadLetterQueue() {
        UUID eventId = UUID.randomUUID();

        publish(eventId, "{\"eventId\": quebrado");

        Message dead = rabbitTemplate.receive(MessagingTopology.USER_REGISTERED_DLQ, TIMEOUT.toMillis());
        assertThat(dead).isNotNull();
        assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
    }
}
