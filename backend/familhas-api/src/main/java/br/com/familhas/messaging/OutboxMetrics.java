package br.com.familhas.messaging;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Métricas do outbox no Actuator (ADR 0012): quantos eventos estão pendentes e quantos desistiram (FAILED).
 * FAILED acima de zero pede ação manual (reprocessar, ADR 0005). Cada leitura é um COUNT pelo índice de status.
 * Os contadores de publicação ficam no OutboxPublisher.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository repository, MeterRegistry registry) {
        for (OutboxStatus status : new OutboxStatus[] { OutboxStatus.PENDING, OutboxStatus.FAILED }) {
            Gauge.builder("familhas.outbox.events", repository, r -> r.countByStatus(status))
                    .description("Eventos no outbox por status")
                    .tag("status", status.name())
                    .register(registry);
        }
    }
}
