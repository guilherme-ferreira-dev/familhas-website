package br.com.familhas.retention;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Limpeza da tabela processed_event (guia, Fase 11; ADR 0012). Uma linha só serve para barrar a entrega repetida
 * do mesmo evento, que chega em minutos ou horas (reenvio do outbox ou do broker). Depois do prazo, sai em lotes.
 * Mensagens na DLQ não têm linha aqui (o rollback a desfez), então reprocessar a DLQ depois da limpeza é seguro.
 */
@Component
@ConditionalOnProperty(prefix = "familhas.retention", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProcessedEventRetention {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventRetention.class);

    static final String DELETE_BATCH = "DELETE FROM processed_event WHERE processed_at < ? AND ROWNUM <= ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Duration retention;
    private final int batchSize;

    public ProcessedEventRetention(JdbcTemplate jdbc, Clock clock, MeterRegistry meterRegistry,
            @Value("${familhas.retention.processed-event:P90D}") Duration retention,
            @Value("${familhas.retention.batch-size:500}") int batchSize) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${familhas.retention.cron:0 45 3 * * *}", zone = "America/Sao_Paulo")
    public int purge() {
        var cutoff = clock.instant().minus(retention).atOffset(ZoneOffset.UTC);
        int total = 0;
        int batch;
        do {
            batch = jdbc.update(DELETE_BATCH, cutoff, batchSize);
            total += batch;
        } while (batch >= batchSize);
        if (total > 0) {
            meterRegistry.counter("familhas.retention.deleted", "table", "processed_event").increment(total);
        }
        log.info("Retencao de processed_event: {} linha(s) apagada(s)", total);
        return total;
    }
}
