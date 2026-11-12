package br.com.familhas.email;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuração do familhas-email (prefixo familhas.email).
 *
 * @param from  remetente, ex.: "Familhas &lt;nao-responda@familhas.com.br&gt;"
 * @param retry retry com backoff exponencial para falhas transitórias
 */
@Validated
@ConfigurationProperties(prefix = "familhas.email")
public record EmailProperties(@NotBlank String from, Retry retry) {

    public EmailProperties {
        retry = retry == null ? new Retry(0, null, 0, null) : retry;
    }

    /**
     * @param maxAttempts     total de tentativas, incluindo a primeira
     * @param initialInterval espera antes da segunda tentativa
     * @param multiplier      fator de crescimento da espera
     * @param maxInterval     teto da espera
     */
    public record Retry(int maxAttempts, Duration initialInterval, double multiplier, Duration maxInterval) {

        public Retry {
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            initialInterval = initialInterval == null ? Duration.ofSeconds(1) : initialInterval;
            multiplier = multiplier < 1.0 ? 2.0 : multiplier;
            maxInterval = maxInterval == null ? Duration.ofSeconds(10) : maxInterval;
        }
    }
}
