package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import br.com.familhas.enums.CarCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** GET /api/search/cars. passengers exige carros com pelo menos esse número de assentos. */
public record CarSearchParams(
        @NotNull UUID cityId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pickUpDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dropOffDate,
        @Min(1) @Max(9) Integer passengers,
        CarCategory carCategory,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice) {

    public int passengersOrDefault() {
        return passengers == null ? 1 : passengers;
    }
}
