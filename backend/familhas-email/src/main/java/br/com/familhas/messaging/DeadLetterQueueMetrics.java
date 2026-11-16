package br.com.familhas.messaging;

import java.util.List;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Mensagens paradas em cada DLQ (ADR 0012). Acima de zero pede investigação e reprocessamento manual
 * (infra/README.md, "Reprocessar"). Cada leitura consulta a fila no broker; sem broker, a métrica fica NaN.
 */
@Component
public class DeadLetterQueueMetrics {

    static final List<String> DEAD_LETTER_QUEUES = List.of(
            MessagingTopology.USER_REGISTERED_DLQ,
            MessagingTopology.BOOKING_CREATED_DLQ,
            MessagingTopology.PAYMENT_CONFIRMED_DLQ,
            MessagingTopology.PAYMENT_FAILED_DLQ);

    public DeadLetterQueueMetrics(AmqpAdmin amqpAdmin, MeterRegistry registry) {
        for (String queue : DEAD_LETTER_QUEUES) {
            Gauge.builder("familhas.email.dlq.messages", amqpAdmin, admin -> depth(admin, queue))
                    .description("Mensagens paradas na DLQ")
                    .tag("queue", queue)
                    .register(registry);
        }
    }

    static double depth(AmqpAdmin admin, String queue) {
        try {
            QueueInformation info = admin.getQueueInfo(queue);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }
}
