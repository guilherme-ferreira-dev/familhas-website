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

    /** Reserva e pagamento (ADR 0007, D10). */
    public static final String BOOKING_CREATED_ROUTING_KEY = "booking.booking-created.v1";
    public static final String BOOKING_CREATED_QUEUE = "familhas-email.booking-created";
    public static final String BOOKING_CREATED_DLQ = BOOKING_CREATED_QUEUE + ".dlq";

    public static final String PAYMENT_CONFIRMED_ROUTING_KEY = "payment.payment-confirmed.v1";
    public static final String PAYMENT_CONFIRMED_QUEUE = "familhas-email.payment-confirmed";
    public static final String PAYMENT_CONFIRMED_DLQ = PAYMENT_CONFIRMED_QUEUE + ".dlq";

    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.payment-failed.v1";
    public static final String PAYMENT_FAILED_QUEUE = "familhas-email.payment-failed";
    public static final String PAYMENT_FAILED_DLQ = PAYMENT_FAILED_QUEUE + ".dlq";

    private MessagingTopology() {
    }
}
