package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** GET /api/search/hotels. Grupo maior que a capacidade do quarto ocupa mais de um quarto. */
public record HotelSearchParams(
        @NotNull UUID cityId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
        @Min(1) @Max(9) Integer guests,
        @Min(1) @Max(5) Integer minStars,
        @DecimalMin("0") BigDecimal minPrice,
        @DecimalMin("0") BigDecimal maxPrice) {

    public int guestsOrDefault() {
        return guests == null ? 1 : guests;
    }
}
