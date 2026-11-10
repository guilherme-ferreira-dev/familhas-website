package br.com.familhas.rating.dto;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Avaliação enviada pelo próprio cliente: o clientId vem do token, nunca do corpo. */
public record ClientRatingRequest(
        @NotNull
        @Min(1)
        @Max(5) Integer rating,
        @NotNull UUID travelPackageId) {
}
