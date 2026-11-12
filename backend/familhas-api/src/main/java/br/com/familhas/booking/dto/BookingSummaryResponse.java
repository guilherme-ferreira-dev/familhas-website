package br.com.familhas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.booking.BookingStatus;
import br.com.familhas.payment.PaymentStatus;

/** Linha da lista "Minhas reservas". */
public record BookingSummaryResponse(UUID id, BookingStatus status, UUID travelPackageId, int passengers,
        String currency, BigDecimal total, LocalDate startDate, PaymentStatus paymentStatus, Instant createdAt) {
}
