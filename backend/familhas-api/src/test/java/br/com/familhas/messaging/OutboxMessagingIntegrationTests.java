package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.client.ClientRepository;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.client.KeycloakClientSyncService.KeycloakUser;
import br.com.familhas.client.event.UserRegisteredEvent;

/**
 * Fluxo real do outbox: Oracle (Flyway V8) + RabbitMQ. O agendamento fica desligado e o teste chama o
 * publisher, para não depender de tempo. Pulado sem Docker.
 */
@SpringBootTest(properties = "familhas.outbox.publisher-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
public class OutboxMessagingIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    static final String TEST_QUEUE = "test.outbox-integration";
    static final String TEST_ROUTING_KEY = "test.bound.v1";

    @Autowired private OutboxService outboxService;
    @Autowired private OutboxPublisher outboxPublisher;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private TopicExchange eventsExchange;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private KeycloakClientSyncService keycloakClientSyncService;
    @Autowired private ClientRepository clientRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        amqpAdmin.declareExchange(eventsExchange);
        // Sem autoDelete: o receive() cancela o consumidor e a fila sumiria entre duas leituras.
        Queue queue = new Queue(TEST_QUEUE, false, false, false);
        amqpAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(eventsExchange).with(TEST_ROUTING_KEY);
        amqpAdmin.declareBinding(binding);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(eventsExchange)
                .with(UserRegisteredEvent.TYPE.routingKey()));
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
        amqpAdmin.purgeQueue(MessagingTopology.UNROUTED_QUEUE, false);
        drainOutbox();
    }

    @Test
    void appendedEventIsPublishedWithConfirmAndMarkedPublished() throws Exception {
        TestEvent event = TestEvent.create(TEST_ROUTING_KEY);

        inTransaction(() -> outboxService.append(event));
        assertThat(outboxEventRepository.findById(event.eventId())).get()
                .extracting(OutboxEventEntity::getStatus).isEqualTo(OutboxStatus.PENDING);

        assertThat(outboxPublisher.publishPending()).isEqualTo(1);

        Message message = rabbitTemplate.receive(TEST_QUEUE, 10_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
        assertThat(message.getMessageProperties().getType()).isEqualTo("TestEvent");
        JsonNode body = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.get("eventId").asText()).isEqualTo(event.eventId().toString());

        OutboxEventEntity row = outboxEventRepository.findById(event.eventId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(outboxPublisher.publishPending()).as("não republica o que já saiu").isZero();
    }

    @Test
    void rolledBackTransactionLeavesNoEvent() {
        TestEvent event = TestEvent.create(TEST_ROUTING_KEY);

        assertThatThrownBy(() -> inTransaction(() -> {
            outboxService.append(event);
            throw new IllegalStateException("falha no domínio");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.findById(event.eventId())).isEmpty();
    }

    @Test
    void appendOutsideTransactionIsRefused() {
        assertThatThrownBy(() -> outboxService.append(TestEvent.create(TEST_ROUTING_KEY)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void eventWithoutBoundQueueGoesToUnroutedQueueInsteadOfBeingLost() {
        TestEvent event = TestEvent.create("test.nobody-listens.v1");

        inTransaction(() -> outboxService.append(event));
        outboxPublisher.publishPending();

        Message message = rabbitTemplate.receive(MessagingTopology.UNROUTED_QUEUE, 10_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.eventId().toString());
    }

    @Test
    void firstLoginPublishesUserRegisteredEventOnlyOnce() throws Exception {
        KeycloakUser user = new KeycloakUser("sub-onboarding-1", "Bia.Nova@Familhas.com", true, "Beatriz Nova",
                "Beatriz");

        java.util.UUID clientId = keycloakClientSyncService.syncClientId(user);
        keycloakClientSyncService.syncClientId(user);

        var rows = outboxEventRepository.findByAggregateTypeAndAggregateId("Client", clientId.toString());
        assertThat(rows).hasSize(1);
        assertThat(outboxPublisher.publishPending()).isEqualTo(1);

        Message message = rabbitTemplate.receive(TEST_QUEUE, 10_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getType()).isEqualTo("UserRegistered");
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("client.user-registered.v1");
        JsonNode body = mapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(body.get("eventId").asText()).isEqualTo(rows.get(0).getId().toString());
        assertThat(body.get("userId").asText()).isEqualTo(clientId.toString());
        assertThat(body.get("email").asText()).isEqualTo("bia.nova@familhas.com");
        assertThat(body.get("firstName").asText()).isEqualTo("Beatriz");
        assertThat(body.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 500)).as("segundo login não gera evento").isNull();
    }

    @Test
    void linkingClientRegisteredByAdminDoesNotPublish() {
        // Seed V2: cliente cadastrado pelo Enéas, ainda sem login.
        var seeded = clientRepository.findByEmailIgnoreCase("ana.souza@familhas.com").orElseThrow();

        keycloakClientSyncService.syncClientId(
                new KeycloakUser("sub-ana-link", "ana.souza@familhas.com", true, "Ana Souza", "Ana"));

        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateId("Client", seeded.getId().toString()))
                .isEmpty();
    }

    protected void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    /** Publica o que sobrou de outros testes da mesma classe, para cada teste partir do zero. */
    protected void drainOutbox() {
        while (outboxPublisher.publishPending() > 0) {
            // segue até esvaziar
        }
        amqpAdmin.purgeQueue(TEST_QUEUE, false);
        amqpAdmin.purgeQueue(MessagingTopology.UNROUTED_QUEUE, false);
    }
}
