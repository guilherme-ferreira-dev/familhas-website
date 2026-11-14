package br.com.familhas.admin.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.admin.PricedItemResponse;
import br.com.familhas.admin.payment.AdminPaymentResponse;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.booking.CancellationReason;
import br.com.familhas.payment.PaymentStatus;

/** Reserva vista pelo Admin: snapshot completo (custo, margem, descontos) e todas as tentativas de pagamento. */
public record AdminBookingDetailResponse(
        UUID id,
        BookingStatus status,
        ClientRef client,
        UUID travelPackageId,
        String travelPackageTitle,
        int passengers,
        String currency,
        BigDecimal totalAmount,
        LocalDate startDate,
        Instant expiresAt,
        PaymentStatus latestPaymentStatus,
        Instant createdAt,
        BigDecimal componentsTotal,
        BigDecimal marginPercent,
        BigDecimal marginAmount,
        BigDecimal promotionPercent,
        BigDecimal discountAmount,
        BigDecimal serviceFeeAmount,
        UUID quoteId,
        Instant confirmedAt,
        Instant cancelledAt,
        CancellationReason cancellationReason,
        List<PricedItemResponse> items,
        List<AdminPaymentResponse> payments) {
}
