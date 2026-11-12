package br.com.familhas.messaging;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executa o efeito de um evento no máximo uma vez por (eventId, consumidor).
 *
 * A linha em processed_event entra antes do efeito, na mesma transação:
 * - efeito falhou: rollback, a linha some e o evento pode ser tentado de novo;
 * - mesma mensagem reentregue depois do commit: a chave primária barra e o efeito não roda;
 * - duas entregas simultâneas: a segunda espera o lock da chave e depois é barrada.
 *
 * Única janela de duplicidade: o efeito externo (SMTP) conclui e o commit falha em seguida.
 * Isso é aceito e documentado no ADR 0005 (entrega "pelo menos uma vez").
 */
@Component
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public IdempotentEventProcessor(ProcessedEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Retorna true se o efeito rodou agora; false se o evento já tinha sido processado. */
    @Transactional
    public boolean process(UUID eventId, String consumer, String eventType, Runnable effect) {
        if (!repository.tryRegister(eventId, consumer, eventType, clock.instant())) {
            log.info("Evento ja processado, ignorado: consumer={} type={}", consumer, eventType);
            return false;
        }
        effect.run();
        log.info("Evento processado: consumer={} type={}", consumer, eventType);
        return true;
    }
}
