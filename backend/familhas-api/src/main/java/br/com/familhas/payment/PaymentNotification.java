package br.com.familhas.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fato informado pelo provedor sobre uma tentativa de pagamento, vindo de webhook ou da reconciliação.
 * Sem dados de cartão nem dados pessoais: só ids, valores e o código de recusa.
 *
 * @param eventId          event.id do Stripe (chave de idempotência); null na reconciliação
 * @param eventType        tipo original do evento (ex.: checkout.session.completed)
 * @param paymentId        metadata.paymentId, quando presente
 * @param amount           valor pago, para conferir com o pagamento (SUCCEEDED)
 * @param amountRefunded   total estornado até agora (REFUNDED)
 * @param currency         moeda do valor, ISO 4217 em maiúsculas
 * @param failureCode      código de recusa (FAILED), ex.: card_declined
 */
public record PaymentNotification(
        String eventId,
        String eventType,
        Kind kind,
        UUID paymentId,
        String checkoutSessionId,
        String paymentIntentId,
        BigDecimal amount,
        BigDecimal amountRefunded,
        String currency,
        String failureCode) {

    public enum Kind {
        SUCCEEDED,
        PROCESSING,
        FAILED,
        SESSION_EXPIRED,
        REFUNDED,
        /** Tipo não tratado: registrado e ignorado. */
        IGNORED
    }

    public static PaymentNotification ignored(String eventId, String eventType) {
        return new PaymentNotification(eventId, eventType, Kind.IGNORED, null, null, null, null, null, null, null);
    }
}
