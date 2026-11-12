package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTests {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private OutboxMessageSender sender;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final OutboxProperties properties = new OutboxProperties(true, Duration.ofSeconds(1), 10,
            Duration.ofSeconds(5), 3, Duration.ofSeconds(2), Duration.ofSeconds(5));

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, sender, properties, Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    @Test
    void publishesWholeBatchAndMarksRowsPublished() {
        OutboxEventEntity first = pending();
        OutboxEventEntity second = pending();
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(first, second));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(2);
        assertThat(List.of(first, second)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(row.getPublishedAt()).isEqualTo(NOW);
            assertThat(row.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void stopsBatchOnFirstFailureAndSchedulesRetryWithBackoff() {
        OutboxEventEntity failing = pending();
        OutboxEventEntity untouched = pending();
        when(repository.lockNextBatch(any(), anyInt())).thenReturn(List.of(failing, untouched));
        doThrow(new AmqpConnectException(new java.net.ConnectException("Connection refused")))
                .when(sender).send(failing);

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(failing.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getAvailableAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(failing.getLastError()).isEqualTo("ConnectException: Connection refused");
        verify(sender, never()).send(eq(untouched));
        assertThat(untouched.getAttempts()).isZero();
    }

    @Test
    void marksFailedAfterMaxAttempts() {
        OutboxEventEntity row = pending();
        when(repository.lockNextBatch(any(), anyInt())).thenReturn(List.of(row));
        doThrow(new AmqpConnectException(new java.net.ConnectException("down"))).when(sender).send(row);

        publisher.publishPending();
        publisher.publishPending();
        publisher.publishPending();

        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void backoffDoublesUpToTheCap() {
        assertThat(properties.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.backoffFor(3)).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.backoffFor(50)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void longErrorMessagesAreTruncatedToColumnSize() {
        OutboxEventEntity row = pending();
        row.markAttemptFailed("x".repeat(900), NOW, 5);

        assertThat(row.getLastError()).hasSize(OutboxEventEntity.LAST_ERROR_MAX_LENGTH);
    }

    private static OutboxEventEntity pending() {
        TestEvent event = TestEvent.create("test.any.v1");
        return OutboxEventEntity.pending(event, "{}", event.eventId().toString(), NOW);
    }
}
