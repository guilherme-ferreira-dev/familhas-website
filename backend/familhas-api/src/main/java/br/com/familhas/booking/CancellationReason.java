package br.com.familhas.booking;

public enum CancellationReason {
    /** Prazo de pagamento vencido sem pagamento aprovado. */
    PAYMENT_EXPIRED,
    /** Estorno total do pagamento. */
    REFUNDED
}
