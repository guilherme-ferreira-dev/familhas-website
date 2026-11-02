package br.com.familhas.travelPackage.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Filtros de GET /travel-packages. Todos opcionais e combinados com AND.
 * minPrice/maxPrice comparam o preço efetivo: pricePromotion quando existir, senão price.
 */
public record TravelPackageFilter(
        UUID originCityId,
        UUID destinationCityId,
        UUID categoryId,
        EntityStatus status,
        @DecimalMin("0.0") BigDecimal minPrice,
        @DecimalMin("0.0") BigDecimal maxPrice,
        @Size(max = 100) String q) {

    public static TravelPackageFilter empty() {
        return new TravelPackageFilter(null, null, null, null, null, null, null);
    }
}
