package br.com.familhas.payment.dto;

import br.com.familhas.payment.PaymentMethod;
import jakarta.validation.constraints.NotNull;

/** POST /api/me/bookings/{id}/checkout-session. Parcelamento não existe nesta versão (ADR 0008). */
public record CheckoutSessionRequest(@NotNull PaymentMethod paymentMethod) {
}
