package br.com.familhas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IdempotentEventProcessorTests {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();

    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final IdempotentEventProcessor processor = new IdempotentEventProcessor(repository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final AtomicInteger effects = new AtomicInteger();

    @Test
    void runsEffectWhenEventIsNew() {
        when(repository.tryRegister(EVENT_ID, "welcome-email", "UserRegistered", NOW)).thenReturn(true);

        boolean ran = processor.process(EVENT_ID, "welcome-email", "UserRegistered", effects::incrementAndGet);

        assertThat(ran).isTrue();
        assertThat(effects).hasValue(1);
    }

    @Test
    void skipsEffectWhenEventWasAlreadyProcessed() {
        when(repository.tryRegister(EVENT_ID, "welcome-email", "UserRegistered", NOW)).thenReturn(false);

        boolean ran = processor.process(EVENT_ID, "welcome-email", "UserRegistered", effects::incrementAndGet);

        assertThat(ran).isFalse();
        assertThat(effects).hasValue(0);
    }

    @Test
    void effectFailurePropagatesSoTheTransactionRollsBack() {
        when(repository.tryRegister(EVENT_ID, "welcome-email", "UserRegistered", NOW)).thenReturn(true);

        assertThatThrownBy(() -> processor.process(EVENT_ID, "welcome-email", "UserRegistered", () -> {
            throw new TransientProcessingException("smtp fora");
        })).isInstanceOf(TransientProcessingException.class);
    }

    @Test
    void uuidIsStoredAsSixteenBigEndianBytes() {
        byte[] bytes = ProcessedEventRepository.toBytes(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"));

        assertThat(bytes).hasSize(16);
        assertThat(bytes[0]).isEqualTo((byte) 0x00);
        assertThat(bytes[15]).isEqualTo((byte) 0xff);
    }
}
