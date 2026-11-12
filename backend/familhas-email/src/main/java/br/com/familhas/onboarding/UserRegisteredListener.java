package br.com.familhas.onboarding;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.familhas.messaging.EventLogContext;
import br.com.familhas.messaging.EventMessageReader;
import br.com.familhas.messaging.IdempotentEventProcessor;
import br.com.familhas.messaging.MessagingTopology;

/**
 * Consumidor do UserRegisteredEvent: valida, garante idempotência por eventId e envia as boas-vindas.
 * Retry, DLQ e logs de falha ficam no container (config.RabbitConfig).
 */
@Component
public class UserRegisteredListener {

    static final String CONSUMER = "welcome-email";

    private final EventMessageReader reader;
    private final IdempotentEventProcessor processor;
    private final WelcomeEmailService welcomeEmailService;

    public UserRegisteredListener(EventMessageReader reader, IdempotentEventProcessor processor,
            WelcomeEmailService welcomeEmailService) {
        this.reader = reader;
        this.processor = processor;
        this.welcomeEmailService = welcomeEmailService;
    }

    @RabbitListener(id = "user-registered", queues = MessagingTopology.USER_REGISTERED_QUEUE)
    public void onMessage(Message message) {
        try (EventLogContext ignored = EventLogContext.open(message)) {
            UserRegisteredEvent event = reader.read(message, UserRegisteredEvent.class);
            processor.process(event.eventId(), CONSUMER, UserRegisteredEvent.TYPE,
                    () -> welcomeEmailService.send(event));
        }
    }
}
