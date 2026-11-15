package br.com.familhas.ratelimit;

/**
 * Balde de fichas: começa cheio ({@code capacity}) e repõe {@code capacity} fichas por minuto, de forma
 * contínua. Cada requisição gasta uma. Rajadas curtas passam; uso sustentado acima do limite recebe 429.
 */
final class TokenBucket {

    private static final long NANOS_PER_MINUTE = 60_000_000_000L;

    private final long capacity;
    private final double tokensPerNano;
    private double tokens;
    private long lastRefillNanos;

    TokenBucket(long capacityPerMinute, long nowNanos) {
        this.capacity = capacityPerMinute;
        this.tokensPerNano = (double) capacityPerMinute / NANOS_PER_MINUTE;
        this.tokens = capacityPerMinute;
        this.lastRefillNanos = nowNanos;
    }

    /** Gasta uma ficha. Retorna 0 se passou, ou quantos nanos faltam para a próxima ficha. */
    synchronized long tryConsume(long nowNanos) {
        long elapsed = Math.max(0, nowNanos - lastRefillNanos);
        tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
        lastRefillNanos = nowNanos;
        if (tokens >= 1) {
            tokens -= 1;
            return 0;
        }
        return (long) Math.ceil((1 - tokens) / tokensPerNano);
    }
}
