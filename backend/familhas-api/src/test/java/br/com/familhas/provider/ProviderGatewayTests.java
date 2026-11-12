package br.com.familhas.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

class ProviderGatewayTests {

    record Offer(String offerId, String provider) implements ProviderOffer {
    }

    /** Provedor de teste com comportamento programável. */
    static class FakeProvider implements Provider<String, Offer> {
        final String name;
        final AtomicInteger calls = new AtomicInteger();
        volatile RuntimeException failure;
        volatile long delayMillis;

        FakeProvider(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ProviderType type() {
            return ProviderType.HOTEL;
        }

        @Override
        public List<Offer> search(String criteria) {
            calls.incrementAndGet();
            behave();
            return List.of(new Offer(name + ":" + criteria, name));
        }

        @Override
        public Optional<Offer> findOffer(String offerId) {
            calls.incrementAndGet();
            behave();
            return Optional.of(new Offer(offerId, name));
        }

        private void behave() {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private ExecutorService executor;
    private ProviderGateway gateway;
    private final FakeProvider curated = new FakeProvider("curated");
    private final FakeProvider external = new FakeProvider("external");

    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
        TimeLimiterRegistry timeLimiters = TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(200)).cancelRunningFuture(true).build());
        gateway = new ProviderGateway(circuitBreakers, timeLimiters, executor,
                new ProviderProperties(Duration.ofMinutes(1), 100));
    }

    @AfterEach
    void tearDown() {
        executor.close();
    }

    @Test
    void aggregatesOffersFromAllProviders() {
        ProviderResult<Offer> result = gateway.search(List.of(curated, external), "maceio");

        assertThat(result.offers()).extracting(Offer::provider).containsExactly("curated", "external");
        assertThat(result.partial()).isFalse();
    }

    @Test
    void failingProviderIsReportedAndOthersStillAnswer() {
        external.failure = new IllegalStateException("HTTP 500");

        ProviderResult<Offer> result = gateway.search(List.of(curated, external), "maceio");

        assertThat(result.offers()).extracting(Offer::provider).containsExactly("curated");
        assertThat(result.unavailableProviders()).containsExactly("external");
    }

    @Test
    void slowProviderTimesOut() {
        external.delayMillis = 2_000;

        long start = System.nanoTime();
        ProviderResult<Offer> result = gateway.search(List.of(curated, external), "maceio");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
        assertThat(result.unavailableProviders()).containsExactly("external");
    }

    @Test
    void successfulSearchesAreCachedAndFailuresAreNot() {
        gateway.search(List.of(curated), "maceio");
        gateway.search(List.of(curated), "maceio");
        gateway.search(List.of(curated), "santiago");
        assertThat(curated.calls).hasValue(2);

        external.failure = new IllegalStateException("fora");
        gateway.search(List.of(external), "maceio");
        external.failure = null;
        ProviderResult<Offer> recovered = gateway.search(List.of(external), "maceio");
        assertThat(recovered.offers()).hasSize(1);
        assertThat(external.calls).hasValue(2);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheProvider() {
        external.failure = new IllegalStateException("fora");
        for (int i = 0; i < 4; i++) {
            gateway.search(List.of(external), "criterio-" + i);
        }
        int callsBeforeOpen = external.calls.get();

        ProviderResult<Offer> result = gateway.search(List.of(external), "outro");

        assertThat(result.unavailableProviders()).containsExactly("external");
        assertThat(external.calls).hasValue(callsBeforeOpen);
    }

    @Test
    void findOfferGoesToTheOwnerProviderWithoutCache() {
        gateway.findOffer(List.of(curated, external), "external:abc");
        gateway.findOffer(List.of(curated, external), "external:abc");

        assertThat(external.calls).hasValue(2);
        assertThat(curated.calls).hasValue(0);
        assertThat(gateway.findOffer(List.of(curated), "desconhecido:1")).isEmpty();
    }

    @Test
    void findOfferOnFailingProviderIsUnavailable() {
        external.failure = new IllegalStateException("fora");

        assertThatThrownBy(() -> gateway.findOffer(List.of(external), "external:abc"))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
