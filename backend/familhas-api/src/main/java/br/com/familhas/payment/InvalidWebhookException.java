package br.com.familhas.payment;

/** Webhook com assinatura ausente ou inválida, velho demais ou ilegível: 400, sem processar. */
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}
