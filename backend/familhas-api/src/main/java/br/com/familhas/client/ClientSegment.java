package br.com.familhas.client;

/**
 * Abas de clientes do Admin (guia, Fase 10; ADR 0011). Exclusivas, nesta ordem de prioridade:
 * <ul>
 * <li>ACTIVE: tem reserva PENDING_PAYMENT ou CONFIRMED;</li>
 * <li>NEW: não é ativo, está ACTIVE, foi cadastrado há {@link #NEW_CLIENT_DAYS} dias ou menos e não tem reserva
 * concluída;</li>
 * <li>PAST: não é ativo nem novo, e tem reserva COMPLETED ou CANCELLED, ou está INACTIVE.</li>
 * </ul>
 * Cliente antigo sem nenhuma reserva não cai em aba nenhuma: aparece só na lista completa.
 */
public enum ClientSegment {
    NEW,
    ACTIVE,
    PAST;

    public static final int NEW_CLIENT_DAYS = 30;
}
