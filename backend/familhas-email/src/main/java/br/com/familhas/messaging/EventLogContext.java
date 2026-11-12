package br.com.familhas.messaging;

import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/**
 * Coloca eventId e correlationId no MDC enquanto a mensagem é processada. São os únicos identificadores
 * que os logs do consumidor usam (LGPD: nada de e-mail ou nome).
 */
public final class EventLogContext implements AutoCloseable {

    public static final String EVENT_ID = "eventId";
    public static final String CORRELATION_ID = "correlationId";

    private EventLogContext() {
    }

    public static EventLogContext open(Message message) {
        MessageProperties props = message.getMessageProperties();
        put(EVENT_ID, props.getMessageId());
        put(CORRELATION_ID, props.getCorrelationId());
        return new EventLogContext();
    }

    /** Resumo seguro de uma mensagem para log, no lugar de Message.toString() (que imprime o corpo). */
    public static String describe(Message message) {
        if (message == null) {
            return "eventId=? correlationId=?";
        }
        MessageProperties props = message.getMessageProperties();
        return "eventId=" + props.getMessageId() + " correlationId=" + props.getCorrelationId()
                + " routingKey=" + props.getReceivedRoutingKey();
    }

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    @Override
    public void close() {
        MDC.remove(EVENT_ID);
        MDC.remove(CORRELATION_ID);
    }
}
