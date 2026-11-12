package br.com.familhas.flight.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.enums.EntityStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FlightRequest(
        @NotBlank
        @Size(max = 120) String companyName,
        @NotNull LocalDateTime boardingDate,
        @NotNull LocalDateTime returnDate,
        @NotBlank
        @Size(max = 120) String boardingAirport,
        @NotBlank
        @Size(max = 120) String returnAirport,
        @NotNull UUID originCityId,
        @NotNull UUID destinationCityId,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal pricePerPassenger,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code, e.g. BRL") String currency,
        @NotNull
        @Min(0)
        @Max(1000) Integer seatsAvailable,
        @NotNull EntityStatus status) {
}
