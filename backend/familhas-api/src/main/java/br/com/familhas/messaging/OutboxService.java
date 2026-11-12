package br.com.familhas.messaging;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada do outbox: grava o evento na transação de quem chama.
 * MANDATORY garante que nunca haja evento sem a mudança de domínio (nem o contrário).
 */
@Service
public class OutboxService {

    /** Chave do MDC com o correlationId da requisição (o filtro de correlação entra na Fase 11). */
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository repository;
    private final EventSerializer serializer;
    private final Clock clock;

    public OutboxService(OutboxEventRepository repository, EventSerializer serializer, Clock clock) {
        this.repository = repository;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(IntegrationEvent event) {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = event.eventId().toString();
        }
        repository.save(OutboxEventEntity.pending(event, serializer.toJson(event), correlationId, clock.instant()));
        log.info("Evento gravado no outbox: type={} eventId={} correlationId={}", event.type().name(),
                event.eventId(), correlationId);
    }
}
