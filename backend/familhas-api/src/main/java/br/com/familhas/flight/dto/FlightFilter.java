package br.com.familhas.flight.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Size;

/**
 * Filtros de GET /flights. Datas no formato ISO (2026-09-10), ambas inclusivas.
 * q busca no nome da companhia.
 */
public record FlightFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureTo,
        @Size(max = 100) String q) {

    public static FlightFilter empty() {
        return new FlightFilter(null, null, null);
    }
}
