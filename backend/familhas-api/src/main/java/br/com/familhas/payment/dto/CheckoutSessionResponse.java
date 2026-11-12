package br.com.familhas.payment.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.payment.PaymentMethod;

/** O front só redireciona para checkoutUrl; a confirmação chega por webhook. */
public record CheckoutSessionResponse(UUID paymentId, PaymentMethod paymentMethod, String checkoutUrl,
        Instant expiresAt) {
}
