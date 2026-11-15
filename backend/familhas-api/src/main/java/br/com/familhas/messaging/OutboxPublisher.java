package br.com.familhas.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publica o outbox em lotes. Cada lote roda numa transação: trava as linhas (SKIP LOCKED), publica uma a uma
 * com confirm e grava o resultado no commit.
 *
 * Na primeira falha o lote para: se o broker caiu, as próximas também falhariam, e cada uma esperaria o
 * timeout do confirm. A linha que falhou ganha backoff exponencial; as demais seguem pendentes para o próximo
 * ciclo. A entrega é "pelo menos uma vez": se o commit falhar depois do confirm, a mensagem sai de novo, e os
 * consumidores são idempotentes por eventId.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final OutboxMessageSender sender;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Counter publishedCounter;
    private final Counter failedAttemptsCounter;
    private final Counter abandonedCounter;

    public OutboxPublisher(OutboxEventRepository repository, OutboxMessageSender sender,
            OutboxProperties properties, Clock clock, PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Métricas (ADR 0012): publicados, tentativas com erro e eventos que desistiram (viraram FAILED).
        this.publishedCounter = Counter.builder("familhas.outbox.published")
                .description("Eventos do outbox confirmados pelo broker").register(meterRegistry);
        this.failedAttemptsCounter = Counter.builder("familhas.outbox.publish.failures")
                .description("Tentativas de publicacao do outbox com erro").register(meterRegistry);
        this.abandonedCounter = Counter.builder("familhas.outbox.abandoned")
                .description("Eventos do outbox que esgotaram as tentativas (FAILED)").register(meterRegistry);
    }

    /** Publica um lote e retorna quantas mensagens foram confirmadas pelo broker. */
    public int publishPending() {
        Integer published = transactionTemplate.execute(status -> publishBatch());
        return published == null ? 0 : published;
    }

    private int publishBatch() {
        Instant now = clock.instant();
        List<OutboxEventEntity> batch = repository.lockNextBatch(now, properties.batchSize());
        int published = 0;
        for (OutboxEventEntity event : batch) {
            // correlationId da requisição de origem nos logs do publisher (ADR 0012).
            MDC.put(OutboxService.CORRELATION_ID_MDC_KEY, event.getCorrelationId());
            try {
                sender.send(event);
                event.markPublished(clock.instant());
                publishedCounter.increment();
                published++;
                log.debug("Evento publicado: type={} eventId={} correlationId={}", event.getEventType(),
                        event.getId(), event.getCorrelationId());
            } catch (RuntimeException ex) {
                int attempt = event.getAttempts() + 1;
                Instant nextAttempt = clock.instant().plus(properties.backoffFor(attempt));
                event.markAttemptFailed(describe(ex), nextAttempt, properties.maxAttempts());
                failedAttemptsCounter.increment();
                if (event.getStatus() == OutboxStatus.FAILED) {
                    abandonedCounter.increment();
                    log.error("Evento desistido apos {} tentativas: type={} eventId={} correlationId={} erro={}",
                            event.getAttempts(), event.getEventType(), event.getId(), event.getCorrelationId(),
                            ex.getClass().getSimpleName());
                } else {
                    log.warn("Falha ao publicar evento (tentativa {}): type={} eventId={} correlationId={} erro={}",
                            event.getAttempts(), event.getEventType(), event.getId(), event.getCorrelationId(),
                            ex.getClass().getSimpleName());
                }
                break;
            } finally {
                MDC.remove(OutboxService.CORRELATION_ID_MDC_KEY);
            }
        }
        if (published > 0) {
            log.info("Outbox: {} evento(s) publicado(s)", published);
        }
        return published;
    }

    /** Só tipo e mensagem da exceção do broker: o payload nunca entra aqui. */
    private static String describe(RuntimeException ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
