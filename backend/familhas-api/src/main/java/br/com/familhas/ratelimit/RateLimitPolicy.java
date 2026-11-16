package br.com.familhas.ratelimit;

/** Grupos de rota com limite próprio (ADR 0012). */
public enum RateLimitPolicy {
    /** Catálogo e busca (GET /api/**): por usuário quando há token, senão por IP. */
    PUBLIC,
    /** Leitura da área do cliente (GET /api/me/**), inclusive o polling da confirmação de pagamento. */
    CLIENT_READ,
    /** Escrita da área do cliente: cotação, reserva, checkout, favoritos e avaliações. */
    CLIENT_WRITE,
    /** /api/admin/** e /api/crm/** (equipe interna): limite alto, só contra script descontrolado ou token vazado. */
    ADMIN
}
