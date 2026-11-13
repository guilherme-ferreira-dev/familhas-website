package br.com.familhas.admin.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import br.com.familhas.booking.BookingStatus;

/** Filtros da lista de reservas do Admin. O período filtra pela criação da reserva. */
public record AdminBookingFilter(
        BookingStatus status,
        UUID clientId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
}
