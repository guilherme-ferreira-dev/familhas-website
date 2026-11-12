package br.com.familhas.quote;

public enum QuoteStatus {
    /** Dentro da validade. */
    OPEN,
    /** Passou de expiresAt: continua consultável, mas não pode virar reserva. */
    EXPIRED,
    /** Virou reserva (ADR 0007). Uma cotação vira no máximo uma reserva. */
    BOOKED
}
