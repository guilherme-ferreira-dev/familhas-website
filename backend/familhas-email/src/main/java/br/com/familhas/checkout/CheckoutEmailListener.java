package br.com.familhas.checkout;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.familhas.messaging.EventLogContext;
import br.com.familhas.messaging.EventMessageReader;
import br.com.familhas.messaging.IdempotentEventProcessor;
import br.com.familhas.messaging.MessagingTopology;

/**
 * Consumidores dos eventos de reserva e pagamento: validam, garantem idempotência por eventId e enviam o
 * e-mail. Retry, DLQ e logs de falha ficam no container (config.RabbitConfig), como no UserRegistered.
 */
@Component
public class CheckoutEmailListener {

    static final String BOOKING_CREATED_CONSUMER = "booking-created-email";
    static final String PAYMENT_CONFIRMED_CONSUMER = "payment-confirmed-email";
    static final String PAYMENT_FAILED_CONSUMER = "payment-failed-email";

    private final EventMessageReader reader;
    private final IdempotentEventProcessor processor;
    private final CheckoutEmails emails;

    public CheckoutEmailListener(EventMessageReader reader, IdempotentEventProcessor processor,
            CheckoutEmails emails) {
        this.reader = reader;
        this.processor = processor;
        this.emails = emails;
    }

    @RabbitListener(id = "booking-created", queues = MessagingTopology.BOOKING_CREATED_QUEUE)
    public void onBookingCreated(Message message) {
        try (EventLogContext ignored = EventLogContext.open(message)) {
            BookingCreatedEvent event = reader.read(message, BookingCreatedEvent.class);
            processor.process(event.eventId(), BOOKING_CREATED_CONSUMER, BookingCreatedEvent.TYPE,
                    () -> emails.bookingCreated(event));
        }
    }

    @RabbitListener(id = "payment-confirmed", queues = MessagingTopology.PAYMENT_CONFIRMED_QUEUE)
    public void onPaymentConfirmed(Message message) {
        try (EventLogContext ignored = EventLogContext.open(message)) {
            PaymentConfirmedEvent event = reader.read(message, PaymentConfirmedEvent.class);
            processor.process(event.eventId(), PAYMENT_CONFIRMED_CONSUMER, PaymentConfirmedEvent.TYPE,
                    () -> emails.paymentConfirmed(event));
        }
    }

    @RabbitListener(id = "payment-failed", queues = MessagingTopology.PAYMENT_FAILED_QUEUE)
    public void onPaymentFailed(Message message) {
        try (EventLogContext ignored = EventLogContext.open(message)) {
            PaymentFailedEvent event = reader.read(message, PaymentFailedEvent.class);
            processor.process(event.eventId(), PAYMENT_FAILED_CONSUMER, PaymentFailedEvent.TYPE,
                    () -> emails.paymentFailed(event));
        }
    }
}
