package br.com.familhas.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica uma linha do outbox no exchange familhas.events e espera o publisher confirm.
 * Só retorna sem exceção quando o broker confirmou que a mensagem foi aceita.
 */
@Component
public class OutboxMessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxMessageSender(RabbitTemplate rabbitTemplate, OutboxProperties properties, Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void send(OutboxEventEntity event) {
        Message message = toMessage(event);
        rabbitTemplate.invoke(operations -> {
            operations.send(MessagingTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message);
            operations.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
            return null;
        });
    }

    Message toMessage(OutboxEventEntity event) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setMessageId(event.getId().toString());
        props.setType(event.getEventType());
        props.setCorrelationId(event.getCorrelationId());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setTimestamp(Date.from(clock.instant()));
        props.setHeader(MessagingTopology.SCHEMA_VERSION_HEADER, event.getSchemaVersion());
        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), props);
    }
}
