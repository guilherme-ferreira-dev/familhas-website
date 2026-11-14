package br.com.familhas.admin.payment.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.familhas.messaging.MessagingTopology;

/**
 * RabbitMQ real: um PaymentConfirmed publicado no exchange de eventos (como o outbox da Fase 6 publica) chega à
 * fila anônima desta instância e vira aviso para as planilhas abertas.
 */
@SpringBootTest(properties = "familhas.admin.payment-feed.enabled=true")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PaymentFeedIntegrationTests {

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired private RabbitTemplate rabbitTemplate;
    @MockitoSpyBean private PaymentFeedBroadcaster broadcaster;

    @Test
    void paymentEventPublishedByTheOutboxReachesTheSpreadsheetFeed() throws Exception {
        byte[] body = Files.readAllBytes(Path.of("../../docs/events/examples/payment-confirmed.v1.json"));
        MessageProperties properties = new MessageProperties();
        properties.setType("PaymentConfirmed");
        properties.setMessageId(UUID.randomUUID().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);

        rabbitTemplate.send(MessagingTopology.EVENTS_EXCHANGE, "payment.payment-confirmed.v1",
                new Message(body, properties));

        ArgumentCaptor<PaymentFeedEvent> event = ArgumentCaptor.forClass(PaymentFeedEvent.class);
        verify(broadcaster, timeout(15_000)).broadcast(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("PaymentConfirmed");
        assertThat(event.getValue().paymentId()).isEqualTo(UUID.fromString("9a7e0000-0000-4000-8000-000000000001"));
    }
}
