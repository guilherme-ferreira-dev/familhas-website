package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import br.com.familhas.client.event.UserRegisteredEvent;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    private OutboxService service;

    @BeforeEach
    void setUp() {
        service = new OutboxService(repository, new EventSerializer(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void appendStoresPendingRowWithEventMetadataAndJsonPayload() {
        UserRegisteredEvent event = UserRegisteredEvent.of(UUID.randomUUID(), "ana@familhas.com", "Ana", NOW);

        service.append(event);

        OutboxEventEntity saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(event.eventId());
        assertThat(saved.isNew()).isTrue();
        assertThat(saved.getAggregateType()).isEqualTo("Client");
        assertThat(saved.getAggregateId()).isEqualTo(event.userId().toString());
        assertThat(saved.getEventType()).isEqualTo("UserRegistered");
        assertThat(saved.getRoutingKey()).isEqualTo("client.user-registered.v1");
        assertThat(saved.getSchemaVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getAvailableAt()).isEqualTo(NOW);
        assertThat(saved.getPayload()).contains("\"eventId\":\"" + event.eventId() + "\"");
    }

    @Test
    void correlationIdComesFromMdcWhenPresent() {
        MDC.put(OutboxService.CORRELATION_ID_MDC_KEY, "req-123");

        service.append(TestEvent.create("test.any.v1"));

        assertThat(captureSaved().getCorrelationId()).isEqualTo("req-123");
    }

    @Test
    void correlationIdFallsBackToEventId() {
        TestEvent event = TestEvent.create("test.any.v1");

        service.append(event);

        assertThat(captureSaved().getCorrelationId()).isEqualTo(event.eventId().toString());
    }

    private OutboxEventEntity captureSaved() {
        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
