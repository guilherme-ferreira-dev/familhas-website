package br.com.familhas.quote;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param validity prazo da cotação a partir da criação (padrão 24 h) */
@ConfigurationProperties(prefix = "familhas.quotes")
public record QuoteProperties(Duration validity) {

    public QuoteProperties {
        validity = validity == null ? Duration.ofHours(24) : validity;
    }
}
