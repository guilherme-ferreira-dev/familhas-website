package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** GET /api/search/flights. returnDate é opcional; minPrice e maxPrice valem sobre o total do grupo. */
public record FlightSearchParams(
        @NotNull UUID originCityId,
        @NotNull UUID destinationCityId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate,
        @Min(1) @Max(9) Integer passengers,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice) {

    public int passengersOrDefault() {
        return passengers == null ? 1 : passengers;
    }
}
