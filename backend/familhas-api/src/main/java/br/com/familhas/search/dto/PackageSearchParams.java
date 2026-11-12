package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** GET /api/search/packages. Todos os filtros são opcionais; datas valem sobre o embarque do voo do pacote. */
public record PackageSearchParams(
        UUID originCityId,
        UUID destinationCityId,
        UUID categoryId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureTo,
        @Min(1) @Max(9) Integer passengers,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice) {

    public int passengersOrDefault() {
        return passengers == null ? 1 : passengers;
    }
}
