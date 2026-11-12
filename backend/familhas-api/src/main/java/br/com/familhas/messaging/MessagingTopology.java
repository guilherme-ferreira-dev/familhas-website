package br.com.familhas.messaging;

/** Nomes da topologia no RabbitMQ (docs/events/README.md). */
public final class MessagingTopology {

    /** Exchange topic de todos os eventos de integração. */
    public static final String EVENTS_EXCHANGE = "familhas.events";

    /** Alternate exchange: recebe o que não tem fila ligada, para nada se perder antes dos consumidores. */
    public static final String UNROUTED_EXCHANGE = "familhas.events.unrouted";
    public static final String UNROUTED_QUEUE = "familhas.events.unrouted";

    /** Header AMQP com a versão do contrato. */
    public static final String SCHEMA_VERSION_HEADER = "x-schema-version";

    private MessagingTopology() {
    }
}
