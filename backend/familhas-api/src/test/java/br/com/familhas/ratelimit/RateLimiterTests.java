package br.com.familhas.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RateLimiterTests {

    private final AtomicLong nanos = new AtomicLong(0);
    private final RateLimitProperties properties = new RateLimitProperties(true, 3, 300, 60, 600, 1000);
    private final RateLimiter limiter = new RateLimiter(properties, nanos::get);

    @Test
    void acceptsABurstUpToTheLimitAndThenAsksToWait() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isZero();
        }
        // 3 por minuto = uma ficha a cada 20 s.
        assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void refillsContinuouslyOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1");
        }
        nanos.addAndGet(Duration.ofSeconds(20).toNanos());

        assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isZero();
        assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isPositive();
    }

    @Test
    void neverStoresMoreThanTheLimit() {
        nanos.addAndGet(Duration.ofHours(1).toNanos());
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isZero();
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1")).isPositive();
    }

    @Test
    void keysAndPoliciesHaveSeparateBuckets() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:1");
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.PUBLIC, "ip:2")).isZero();
        assertThat(limiter.tryAcquire(RateLimitPolicy.CLIENT_WRITE, "ip:1")).isZero();
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource(nullValues = "NONE", value = {
            "GET,     /api/travel-packages,          PUBLIC",
            "GET,     /api/search/packages,          PUBLIC",
            "GET,     /api/me,                       CLIENT_READ",
            "GET,     /api/me/bookings/1/payment,    CLIENT_READ",
            "POST,    /api/me/quotes,                CLIENT_WRITE",
            "PUT,     /api/me/favorites/1,           CLIENT_WRITE",
            "DELETE,  /api/me/favorites/1,           CLIENT_WRITE",
            "GET,     /api/admin/payments,           ADMIN",
            "POST,    /api/admin/quotes,             ADMIN",
            "POST,    /api/webhooks/stripe,          NONE",
            "OPTIONS, /api/me/quotes,                NONE",
            "GET,     /actuator/health,              NONE",
            "GET,     /v3/api-docs/public,           NONE",
            "GET,     /api/merchants,                PUBLIC" })
    void policyByRoute(String method, String path, RateLimitPolicy expected) {
        assertThat(RateLimitFilter.policyFor(method, path)).isEqualTo(expected);
    }
}
