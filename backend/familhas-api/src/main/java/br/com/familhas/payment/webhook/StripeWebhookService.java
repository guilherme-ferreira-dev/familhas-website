package br.com.familhas.payment.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.payment.PaymentNotification;
import br.com.familhas.payment.PaymentNotificationProcessor;
import br.com.familhas.payment.webhook.StripeWebhookEventEntity.Outcome;

/**
 * Processamento idempotente por event.id (ADR 0007, D6). A linha do evento é gravada primeiro, na mesma
 * transação do efeito: um evento repetido em paralelo espera na chave primária e falha, desfazendo tudo.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final StripeWebhookEventRepository repository;
    private final PaymentNotificationProcessor processor;

    public StripeWebhookService(StripeWebhookEventRepository repository, PaymentNotificationProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    /** @return false se o evento já tinha sido processado */
    @Transactional
    public boolean handle(PaymentNotification notification) {
        if (repository.existsById(notification.eventId())) {
            log.info("Webhook repetido ignorado: eventId={} type={}", notification.eventId(), notification.eventType());
            return false;
        }
        StripeWebhookEventEntity event = repository.saveAndFlush(
                new StripeWebhookEventEntity(notification.eventId(), notification.eventType(), Outcome.IGNORED));
        if (processor.apply(notification)) {
            event.markProcessed();
        }
        return true;
    }
}
