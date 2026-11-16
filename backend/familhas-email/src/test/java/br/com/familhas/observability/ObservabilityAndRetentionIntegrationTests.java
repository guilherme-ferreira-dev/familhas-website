package br.com.familhas.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.messaging.ProcessedEventRepository;
import br.com.familhas.retention.ProcessedEventRetention;
import br.com.familhas.support.MessagingIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Fase 11 (ADR 0012): métricas da DLQ e retenção de processed_event, com Oracle e RabbitMQ reais.
 */
@AutoConfigureObservability(tracing = false)
class ObservabilityAndRetentionIntegrationTests extends MessagingIntegrationTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired private MeterRegistry meterRegistry;
    @Autowired private ProcessedEventRetention retention;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void invalidPayloadIsCountedAndTheDlqDepthIsExposed() {
        publish(UUID.randomUUID(), "{\"eventId\":\"nao-e-uuid\"}");

        await().atMost(TIMEOUT).until(() -> dlqDepth() == 1.0);
        assertThat(meterRegistry.find("familhas.email.dead-lettered")
                .tag("queue", MessagingTopology.USER_REGISTERED_QUEUE)
                .counter()).isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isGreaterThanOrEqualTo(1));

        amqpAdmin.purgeQueue(MessagingTopology.USER_REGISTERED_DLQ, false);
        await().atMost(TIMEOUT).until(() -> dlqDepth() == 0.0);
    }

    @Test
    void retentionDeletesOnlyOldRows() {
        UUID recent = UUID.randomUUID();
        processedEventRepository.tryRegister(recent, "test", "TEST", Instant.now().minus(Duration.ofDays(10)));
        UUID[] old = new UUID[3];
        for (int i = 0; i < old.length; i++) {
            old[i] = UUID.randomUUID();
            processedEventRepository.tryRegister(old[i], "test", "TEST", Instant.now().minus(Duration.ofDays(120)));
        }

        int deleted = retention.purge();

        assertThat(deleted).isGreaterThanOrEqualTo(3);
        assertThat(processedEventRepository.exists(recent, "test")).isTrue();
        for (UUID id : old) {
            assertThat(processedEventRepository.exists(id, "test")).isFalse();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_event WHERE processed_at < SYSTIMESTAMP - 90",
                Integer.class)).isZero();
    }

    private double dlqDepth() {
        return meterRegistry.get("familhas.email.dlq.messages")
                .tag("queue", MessagingTopology.USER_REGISTERED_DLQ)
                .gauge().value();
    }
}
