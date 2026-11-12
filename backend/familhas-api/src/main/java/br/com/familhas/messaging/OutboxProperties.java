package br.com.familhas.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do publisher do outbox (prefixo familhas.outbox).
 *
 * @param publisherEnabled liga o agendamento; os testes desligam e chamam o publisher manualmente
 * @param fixedDelay       intervalo entre lotes
 * @param batchSize        linhas por lote
 * @param confirmTimeout   espera máxima pelo publisher confirm de cada mensagem
 * @param maxAttempts      tentativas antes de marcar FAILED
 * @param retryBackoff     espera depois da primeira falha; dobra a cada falha
 * @param maxRetryBackoff  teto da espera entre tentativas
 */
@ConfigurationProperties(prefix = "familhas.outbox")
public record OutboxProperties(
        boolean publisherEnabled,
        Duration fixedDelay,
        int batchSize,
        Duration confirmTimeout,
        int maxAttempts,
        Duration retryBackoff,
        Duration maxRetryBackoff) {

    public OutboxProperties {
        fixedDelay = fixedDelay == null ? Duration.ofSeconds(1) : fixedDelay;
        batchSize = batchSize <= 0 ? 50 : batchSize;
        confirmTimeout = confirmTimeout == null ? Duration.ofSeconds(5) : confirmTimeout;
        maxAttempts = maxAttempts <= 0 ? 20 : maxAttempts;
        retryBackoff = retryBackoff == null ? Duration.ofSeconds(2) : retryBackoff;
        maxRetryBackoff = maxRetryBackoff == null ? Duration.ofMinutes(5) : maxRetryBackoff;
    }

    /** Espera antes da próxima tentativa, depois de {@code attempts} falhas (1, 2, 3...). */
    public Duration backoffFor(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = retryBackoff.multipliedBy(1L << exponent);
        return delay.compareTo(maxRetryBackoff) > 0 ? maxRetryBackoff : delay;
    }
}
