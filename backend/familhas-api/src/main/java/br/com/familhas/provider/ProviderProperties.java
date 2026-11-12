package br.com.familhas.provider;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da camada de provedores (prefixo familhas.providers).
 * Circuit breaker e timeout ficam nas propriedades resilience4j.* (instância provider-&lt;tipo&gt;-&lt;nome&gt;).
 *
 * @param cacheTtl     validade das buscas em cache (por provedor e critério)
 * @param cacheMaxSize número máximo de buscas guardadas
 */
@ConfigurationProperties(prefix = "familhas.providers")
public record ProviderProperties(Duration cacheTtl, long cacheMaxSize) {

    public ProviderProperties {
        cacheTtl = cacheTtl == null ? Duration.ofMinutes(2) : cacheTtl;
        cacheMaxSize = cacheMaxSize <= 0 ? 1000 : cacheMaxSize;
    }
}
