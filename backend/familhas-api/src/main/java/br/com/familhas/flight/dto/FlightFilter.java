package br.com.familhas.flight.dto;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import br.com.familhas.enums.EntityStatus;
import jakarta.validation.constraints.Size;

/**
 * Filtros de GET /flights. Datas no formato ISO (2026-09-10), ambas inclusivas.
 * q busca no nome da companhia.
 */
public record FlightFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureTo,
        UUID originCityId,
        UUID destinationCityId,
        EntityStatus status,
        @Size(max = 100) String q) {

    public static FlightFilter empty() {
        return new FlightFilter(null, null, null, null, null, null);
    }

    /** Catálogo público: só voos ativos, ignorando o status pedido. */
    public FlightFilter activeOnly() {
        return new FlightFilter(departureFrom, departureTo, originCityId, destinationCityId, EntityStatus.ACTIVE, q);
    }
}
