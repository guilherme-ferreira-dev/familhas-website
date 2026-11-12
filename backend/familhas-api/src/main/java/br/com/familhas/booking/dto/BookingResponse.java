package br.com.familhas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;
import br.com.familhas.quote.dto.QuoteResponse;

/**
 * Reserva vista pelo cliente (docs/api/checkout.yaml, BookingResponse). Como na cotação, custo por item e
 * margem ficam só no banco: subtotal já inclui a margem.
 */
public record BookingResponse(
        UUID id,
        BookingStatus status,
        UUID quoteId,
        UUID travelPackageId,
        int passengers,
        List<QuoteResponse.Item> items,
        String currency,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal serviceFee,
        BigDecimal total,
        Instant expiresAt,
        Instant confirmedAt,
        Instant cancelledAt,
        CancellationReason cancellationReason,
        Payment payment,
        Instant createdAt) {

    /** Pagamento mais recente da reserva. installments é sempre 1 (ADR 0008). */
    public record Payment(
            UUID id,
            String method,
            String status,
            int installments,
            BigDecimal amount,
            String currency,
            BigDecimal refundedAmount,
            Instant paidAt) {
    }
}
