package br.com.familhas.carRental.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.Transmission;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CarRentalOfferRequest(
        @NotNull UUID cityId,
        @NotBlank
        @Size(max = 120) String companyName,
        @NotBlank
        @Size(max = 120) String carModel,
        @NotNull CarCategory carCategory,
        @NotNull Transmission transmission,
        @NotNull
        @Min(1)
        @Max(15) Integer seats,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal pricePerDay,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code, e.g. BRL") String currency,
        @NotNull EntityStatus status) {
}
