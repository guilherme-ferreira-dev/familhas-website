package br.com.familhas.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.messaging.MessagingTopology;

/**
 * Infra real compartilhada pelos testes de integração: Oracle (schema próprio via Flyway), RabbitMQ e MailHog.
 * Os containers sobem uma vez por execução. O contexto é fechado ao fim de cada classe para que dois listeners
 * (de contextos diferentes) nunca disputem a mesma fila.
 */
@SpringBootTest(properties = {
        "familhas.email.retry.max-attempts=3",
        "familhas.email.retry.initial-interval=PT0.1S",
        "familhas.email.retry.max-interval=PT0.2S" })
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MessagingIntegrationTestSupport {

    @ServiceConnection
    protected static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @ServiceConnection
    protected static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management");

    protected static final GenericContainer<?> MAILHOG = new GenericContainer<>("mailhog/mailhog:v1.0.1")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v2/messages").forPort(8025));

    static {
        ORACLE.start();
        RABBIT.start();
        MAILHOG.start();
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> MAILHOG.getMappedPort(1025));
    }

    @Autowired protected RabbitTemplate rabbitTemplate;
    @Autowired protected AmqpAdmin amqpAdmin;

    protected final MailHogClient mailHog = new MailHogClient(
            "http://" + MAILHOG.getHost() + ":" + MAILHOG.getMappedPort(8025));

    @BeforeEach
    void purgeDeadLetterQueue() {
        amqpAdmin.purgeQueue(MessagingTopology.USER_REGISTERED_DLQ, false);
    }

    /** Publica como a familhas-api publica (mesmas propriedades AMQP). */
    protected void publish(UUID eventId, String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setMessageId(eventId.toString());
        props.setCorrelationId("corr-" + eventId);
        props.setType("UserRegistered");
        rabbitTemplate.send(MessagingTopology.EVENTS_EXCHANGE, MessagingTopology.USER_REGISTERED_ROUTING_KEY,
                new Message(json.getBytes(StandardCharsets.UTF_8), props));
    }

    protected static String userRegisteredJson(UUID eventId, String email, String firstName) {
        return """
                {"eventId":"%s","userId":"%s","email":"%s","firstName":"%s","occurredAt":"%s","schemaVersion":1}"""
                .formatted(eventId, UUID.randomUUID(), email, firstName, Instant.now());
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.com";
    }
}
