package br.com.familhas.travelPackage.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * currency: ISO 4217 em maiúsculas; ausente = BRL na criação, mantém o atual no update.
 * components: ausente (null) mantém os atuais no update; lista vazia remove todos.
 */
public record TravelPackageRequest(
        @NotBlank
        @Size(max = 180) String title,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal pricePromotion,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code, e.g. BRL") String currency,
        @NotNull EntityStatus status,
        @Size(max = 500) String imageUrl,
        @NotNull UUID flightId,
        @NotNull UUID categoryId,
        @NotNull UUID originCityId,
        @NotNull UUID destinationCityId,
        @Size(max = 10) List<@Valid @NotNull TravelPackageComponentRequest> components) {
}





