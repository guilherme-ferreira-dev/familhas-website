package br.com.familhas.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.booking.BookingStatus;

/** Linha da lista "Minhas reservas". */
public record BookingSummaryResponse(UUID id, BookingStatus status, UUID travelPackageId, int passengers,
        String currency, BigDecimal total, LocalDate startDate, String paymentStatus, Instant createdAt) {
}
