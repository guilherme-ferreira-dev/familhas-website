package br.com.familhas.messaging;

/** Nomes da topologia no RabbitMQ (docs/events/README.md). */
public final class MessagingTopology {

    /** Exchange topic dos eventos. Mesmos argumentos da declaração na familhas-api. */
    public static final String EVENTS_EXCHANGE = "familhas.events";
    public static final String UNROUTED_EXCHANGE = "familhas.events.unrouted";

    /** Dead letter exchange dos consumidores deste serviço. */
    public static final String DEAD_LETTER_EXCHANGE = "familhas.events.dlx";

    public static final String USER_REGISTERED_ROUTING_KEY = "client.user-registered.v1";
    public static final String USER_REGISTERED_QUEUE = "familhas-email.user-registered";
    public static final String USER_REGISTERED_DLQ = USER_REGISTERED_QUEUE + ".dlq";

    private MessagingTopology() {
    }
}
