package br.com.familhas.travelPackage.dto;

import java.util.UUID;

import br.com.familhas.enums.PackageComponentType;

public record TravelPackageComponentResponse(
        UUID id,
        PackageComponentType type,
        UUID roomOfferId,
        UUID carRentalOfferId,
        Integer quantity) {
}
