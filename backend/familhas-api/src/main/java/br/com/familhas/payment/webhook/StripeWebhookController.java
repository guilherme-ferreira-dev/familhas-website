package br.com.familhas.payment.webhook;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.payment.PaymentGateway;
import br.com.familhas.payment.PaymentNotification;

/**
 * Webhooks do Stripe (ADR 0007, D6). Rota pública: a autenticação é a Stripe-Signature, validada sobre o corpo
 * cru (bytes), antes de qualquer desserialização. 200 para processado, repetido ou ignorado; 400 para assinatura
 * inválida; 500 em falha de processamento, e o Stripe tenta de novo.
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentGateway gateway;
    private final StripeWebhookService webhookService;

    public StripeWebhookController(PaymentGateway gateway, StripeWebhookService webhookService) {
        this.gateway = gateway;
        this.webhookService = webhookService;
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        PaymentNotification notification = gateway.parseWebhook(new String(body, StandardCharsets.UTF_8), signature);
        try {
            webhookService.handle(notification);
        } catch (DataIntegrityViolationException ex) {
            // O mesmo event.id chegou em paralelo e a outra entrega gravou primeiro.
            log.info("Webhook repetido em paralelo: eventId={}", notification.eventId());
        }
        return ResponseEntity.ok().build();
    }
}
