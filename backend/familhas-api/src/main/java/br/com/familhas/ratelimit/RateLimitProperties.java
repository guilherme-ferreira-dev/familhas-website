package br.com.familhas.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites por minuto de cada {@link RateLimitPolicy} (ADR 0012). O balde enche de novo de forma contínua,
 * então o valor também é o tamanho da maior rajada aceita.
 */
@ConfigurationProperties(prefix = "familhas.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("300") long publicPerMinute,
        @DefaultValue("300") long clientReadPerMinute,
        @DefaultValue("30") long clientWritePerMinute,
        @DefaultValue("600") long adminPerMinute,
        @DefaultValue("100000") long maxTrackedKeys) {

    public long limitFor(RateLimitPolicy policy) {
        return switch (policy) {
            case PUBLIC -> publicPerMinute;
            case CLIENT_READ -> clientReadPerMinute;
            case CLIENT_WRITE -> clientWritePerMinute;
            case ADMIN -> adminPerMinute;
        };
    }
}
