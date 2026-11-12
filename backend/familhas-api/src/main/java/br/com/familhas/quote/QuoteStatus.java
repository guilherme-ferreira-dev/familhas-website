package br.com.familhas.quote;

public enum QuoteStatus {
    /** Dentro da validade. */
    OPEN,
    /** Passou de expiresAt: continua consultável, mas não pode virar reserva (Fase 6). */
    EXPIRED
}
