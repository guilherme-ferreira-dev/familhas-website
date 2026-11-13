package br.com.familhas.admin.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.admin.ClientRef;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.payment.PaymentStatus;

public record AdminBookingSummaryResponse(
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
        Instant createdAt) {
}
