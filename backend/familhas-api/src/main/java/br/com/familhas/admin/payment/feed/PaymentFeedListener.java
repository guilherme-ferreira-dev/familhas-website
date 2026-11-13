package br.com.familhas.admin.payment.feed;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consome os eventos de pagamento da Fase 6 (PaymentConfirmed, PaymentFailed e futuros payment.*) e avisa as
 * planilhas abertas. Cada instância da API tem a própria fila anônima (exclusiva, apagada ao desconectar), então
 * todas as instâncias recebem todos os eventos e avisam as próprias conexões SSE.
 *
 * Mensagem inválida é descartada com log (sem DLQ: é só um aviso; a planilha também recarrega a cada 30 s).
 */
public class PaymentFeedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFeedListener.class);

    private final PaymentFeedBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public PaymentFeedListener(PaymentFeedBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "#{paymentFeedQueue.name}")
    public void onMessage(Message message) {
        try {
            broadcaster.broadcast(toEvent(message));
        } catch (RuntimeException | IOException e) {
            log.warn("Evento de pagamento ignorado na planilha: messageId={} motivo={}",
                    message.getMessageProperties().getMessageId(), e.getMessage());
        }
    }

    PaymentFeedEvent toEvent(Message message) throws IOException {
        JsonNode body = objectMapper.readTree(message.getBody());
        String type = message.getMessageProperties().getType();
        return new PaymentFeedEvent(
                type != null ? type : message.getMessageProperties().getReceivedRoutingKey(),
                UUID.fromString(required(body, "paymentId")),
                UUID.fromString(required(body, "bookingId")),
                Instant.parse(required(body, "occurredAt")));
    }

    private static String required(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("campo " + field + " ausente");
        }
        return value.asText();
    }
}
