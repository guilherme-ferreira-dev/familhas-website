package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxMessageSenderTests {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void messageCarriesContractPropertiesAndJsonBody() {
        OutboxMessageSender sender = new OutboxMessageSender(Mockito.mock(RabbitTemplate.class),
                new OutboxProperties(true, null, 0, Duration.ofSeconds(5), 0, null, null),
                Clock.fixed(NOW, ZoneOffset.UTC));
        TestEvent event = TestEvent.create("test.any.v1");
        OutboxEventEntity row = OutboxEventEntity.pending(event, "{\"eventId\":\"x\"}", "corr-1", NOW);

        Message message = sender.toMessage(row);

        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"eventId\":\"x\"}");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getType()).isEqualTo("TestEvent");
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("corr-1");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getMessageProperties().getTimestamp()).isEqualTo(Date.from(NOW));
        assertThat((Integer) message.getMessageProperties().getHeader(MessagingTopology.SCHEMA_VERSION_HEADER))
                .isEqualTo(1);
    }
}
