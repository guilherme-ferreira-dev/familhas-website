package br.com.familhas.retention;

import java.time.ZoneOffset;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Limpeza por idade das tabelas que só crescem (guia, Fase 11; ADR 0012).
 *
 * Cada tabela é apagada em lotes de {@code batchSize} (Oracle: ROWNUM), um DELETE por transação, até não sobrar
 * nada vencido. Duas instâncias rodando juntas só dividem o trabalho. Nada de dado pessoal no log: só contagens.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    static final String OUTBOX_PUBLISHED = """
            DELETE FROM outbox_event
             WHERE status = 'PUBLISHED' AND published_at < ? AND ROWNUM <= ?""";

    static final String STRIPE_WEBHOOK_EVENTS = """
            DELETE FROM stripe_webhook_events
             WHERE created_at < ? AND ROWNUM <= ?""";

    /** Cotação com reserva nunca sai: a reserva guarda o vínculo (fk_bookings_quote). */
    static final String EXPIRED_QUOTES = """
            DELETE FROM quotes q
             WHERE q.expires_at < ?
               AND q.status <> 'BOOKED'
               AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.quote_id = q.id)
               AND ROWNUM <= ?""";

    private final JdbcTemplate jdbc;
    private final RetentionProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public RetentionService(JdbcTemplate jdbc, RetentionProperties properties, Clock clock,
            MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    /** Roda as três limpezas e devolve quantas linhas saíram de cada tabela. */
    public Map<String, Integer> purge() {
        Instant now = clock.instant();
        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("outbox_event", purge("outbox_event", OUTBOX_PUBLISHED, now.minus(properties.outboxPublished())));
        deleted.put("stripe_webhook_events",
                purge("stripe_webhook_events", STRIPE_WEBHOOK_EVENTS, now.minus(properties.stripeWebhookEvents())));
        deleted.put("quotes", purge("quotes", EXPIRED_QUOTES, now.minus(properties.expiredQuotes())));
        log.info("Retencao concluida: {}", deleted);
        return deleted;
    }

    private int purge(String table, String sql, Instant cutoff) {
        int total = 0;
        try {
            int batch;
            do {
                batch = jdbc.update(sql, cutoff.atOffset(ZoneOffset.UTC), properties.batchSize());
                total += batch;
            } while (batch >= properties.batchSize());
        } catch (RuntimeException ex) {
            // Uma tabela com erro não impede as outras; o próximo ciclo tenta de novo.
            log.error("Falha na retencao de {} depois de {} linha(s): {}", table, total,
                    ex.getClass().getSimpleName());
            meterRegistry.counter("familhas.retention.failures", "table", table).increment();
        }
        if (total > 0) {
            Counter.builder("familhas.retention.deleted")
                    .description("Linhas apagadas pela retencao")
                    .tag("table", table)
                    .register(meterRegistry)
                    .increment(total);
        }
        return total;
    }
}
