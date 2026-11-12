package br.com.familhas.booking.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** POST /api/me/bookings. O preço vem do snapshot da cotação, nunca do cliente. */
public record BookingRequest(@NotNull UUID quoteId) {
}
