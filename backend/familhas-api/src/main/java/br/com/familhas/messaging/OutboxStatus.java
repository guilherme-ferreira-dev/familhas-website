package br.com.familhas.messaging;

public enum OutboxStatus {
    /** Aguardando publicação (ou nova tentativa depois de available_at). */
    PENDING,
    /** Confirmado pelo broker (publisher confirm). */
    PUBLISHED,
    /** Excedeu o máximo de tentativas. Reprocessar manualmente: status = 'PENDING' (ver ADR 0005). */
    FAILED
}
