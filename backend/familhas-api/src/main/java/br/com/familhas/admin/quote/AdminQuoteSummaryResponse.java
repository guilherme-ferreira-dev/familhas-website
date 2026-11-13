package br.com.familhas.admin.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.quote.QuoteOrigin;
import br.com.familhas.quote.QuoteStatus;

public record AdminQuoteSummaryResponse(UUID id, QuoteStatus status, QuoteOrigin origin, ClientRef client,
        UUID travelPackageId, int passengers, String currency, BigDecimal totalAmount, Instant expiresAt,
        Instant createdAt) {
}
