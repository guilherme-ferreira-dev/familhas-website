package br.com.familhas.payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import br.com.familhas.common.Money;

/**
 * Porta para o provedor de pagamento (guia, Fase 6; ADR 0007). O adapter é o Stripe Checkout hospedado:
 * dados de cartão nunca passam pela API. Os tipos aqui não dependem do SDK do Stripe.
 *
 * Toda operação que escreve no provedor recebe uma idempotency key: repetir a chamada depois de um timeout
 * não cria uma segunda sessão nem um segundo estorno.
 */
public interface PaymentGateway {

    /** false quando não há chave configurada: o checkout responde 503 e o resto da API funciona. */
    boolean isAvailable();

    /**
     * Abre a página de pagamento hospedada.
     *
     * @throws PaymentMethodUnavailableException se o método não está habilitado na conta
     * @throws PaymentGatewayException           se o provedor falhar ou estiver fora
     */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    /** Expira uma sessão ainda aberta. Sessão já concluída ou expirada é ignorada. */
    void expireCheckoutSession(String sessionId, String idempotencyKey);

    /**
     * Estado atual da sessão no provedor, no mesmo formato de um webhook (reconciliação; ADR 0007, D7).
     * Vazio quando a sessão segue aberta sem novidade.
     */
    Optional<PaymentNotification> fetchCheckoutSessionState(String sessionId);

    /** Estorno total do pagamento. */
    void refund(String paymentIntentId, String idempotencyKey);

    /**
     * Valida a assinatura do webhook e traduz o evento.
     *
     * @throws InvalidWebhookException se a assinatura não confere, o evento é velho demais ou o corpo é ilegível
     */
    PaymentNotification parseWebhook(String payload, String signatureHeader);

    record CheckoutRequest(
            UUID paymentId,
            UUID bookingId,
            PaymentMethod method,
            Money amount,
            String description,
            String customerEmail,
            String successUrl,
            String cancelUrl,
            Instant expiresAt,
            String idempotencyKey) {
    }

    record CheckoutSession(String id, String url, Instant expiresAt) {
    }
}
