package br.com.familhas.quote.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.quote.QuoteStatus;

/**
 * Cotação vista pelo cliente. Custo por item e margem ficam só no banco (snapshot para o Admin):
 * subtotal já inclui a margem.
 */
public record QuoteResponse(
        UUID id,
        QuoteStatus status,
        UUID travelPackageId,
        int passengers,
        List<Item> items,
        String currency,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal serviceFee,
        BigDecimal total,
        Instant expiresAt,
        Instant createdAt) {

    public record Item(PriceItemType type, String description, LocalDate startDate, LocalDate endDate,
            int quantity) {
    }
}
