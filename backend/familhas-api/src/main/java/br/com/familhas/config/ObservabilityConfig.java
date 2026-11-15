package br.com.familhas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import br.com.familhas.observability.CorrelationIdFilter;
import br.com.familhas.ratelimit.RateLimitProperties;
import br.com.familhas.ratelimit.RateLimiter;

/** correlationId e rate limiting (ADR 0012). O filtro de rate limit entra na cadeia do SecurityConfig. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class ObservabilityConfig {

    /** Antes de tudo, inclusive do Spring Security: toda resposta e toda linha de log têm o id. */
    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "familhas.rate-limit", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    RateLimiter rateLimiter(RateLimitProperties properties) {
        return new RateLimiter(properties);
    }
}
