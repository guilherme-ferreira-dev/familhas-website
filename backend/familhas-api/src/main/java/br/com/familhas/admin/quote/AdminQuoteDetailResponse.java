package br.com.familhas.admin.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.admin.PricedItemResponse;
import br.com.familhas.quote.QuoteOrigin;
import br.com.familhas.quote.QuoteStatus;

/** Cotação vista pelo Admin: custo, margem, promoção e desconto manual separados. */
public record AdminQuoteDetailResponse(
        UUID id,
        QuoteStatus status,
        QuoteOrigin origin,
        ClientRef client,
        UUID travelPackageId,
        int passengers,
        String currency,
        BigDecimal totalAmount,
        Instant expiresAt,
        Instant createdAt,
        BigDecimal componentsTotal,
        BigDecimal marginPercent,
        BigDecimal marginAmount,
        BigDecimal promotionPercent,
        BigDecimal discountAmount,
        BigDecimal serviceFeeAmount,
        BigDecimal manualDiscountAmount,
        List<PricedItemResponse> items,
        String internalNote,
        String createdBy,
        UUID bookingId) {
}
