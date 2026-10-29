package br.com.familhas.travelPackage.dto;

import java.util.UUID;

import br.com.familhas.enums.PackageComponentType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * HOTEL_STAY exige roomOfferId (quantity = noites); CAR_RENTAL exige carRentalOfferId (quantity = dias).
 */
public record TravelPackageComponentRequest(
        @NotNull PackageComponentType type,
        UUID roomOfferId,
        UUID carRentalOfferId,
        @NotNull
        @Min(1)
        @Max(60) Integer quantity) {
}
