package br.com.familhas.ratelimit;

import java.time.Duration;
import java.util.function.LongSupplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Baldes em memória, um por (política, chave). Caffeine limita a quantidade de chaves e descarta as que
 * ficaram 10 minutos sem uso (nesse tempo qualquer balde já estaria cheio de novo).
 *
 * Limite por instância: com N instâncias atrás de um balanceador, o limite efetivo é até N vezes maior.
 * Limite global (gateway ou Redis) fica como evolução (ADR 0012).
 */
public class RateLimiter {

    private final RateLimitProperties properties;
    private final LongSupplier nanoClock;
    private final Cache<String, TokenBucket> buckets;

    public RateLimiter(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    RateLimiter(RateLimitProperties properties, LongSupplier nanoClock) {
        this.properties = properties;
        this.nanoClock = nanoClock;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedKeys())
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    /** Retorna {@link Duration#ZERO} se a requisição passa, ou quanto o chamador deve esperar. */
    public Duration tryAcquire(RateLimitPolicy policy, String key) {
        long now = nanoClock.getAsLong();
        TokenBucket bucket = buckets.get(policy.name() + '|' + key,
                k -> new TokenBucket(properties.limitFor(policy), now));
        return Duration.ofNanos(bucket.tryConsume(now));
    }
}
