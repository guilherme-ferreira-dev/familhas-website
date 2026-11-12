package br.com.familhas.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.familhas.quote.QuoteStatus;

/** Linha da lista "Minhas cotações". */
public record QuoteSummaryResponse(UUID id, QuoteStatus status, UUID travelPackageId, int passengers,
        String currency, BigDecimal total, Instant expiresAt, Instant createdAt) {
}
