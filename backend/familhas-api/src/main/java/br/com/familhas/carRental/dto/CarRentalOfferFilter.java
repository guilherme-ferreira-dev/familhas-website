package br.com.familhas.carRental.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.Transmission;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Filtros de GET /car-rental-offers. minSeats: carros com ao menos N lugares.
 * maxPricePerDay compara o valor na moeda da própria oferta (sem conversão de câmbio).
 */
public record CarRentalOfferFilter(
        UUID cityId,
        CarCategory carCategory,
        Transmission transmission,
        @Min(1) Integer minSeats,
        @DecimalMin("0.0") BigDecimal maxPricePerDay,
        EntityStatus status) {

    public static CarRentalOfferFilter empty() {
        return new CarRentalOfferFilter(null, null, null, null, null, null);
    }

    /** Catalogo publico: mantem os filtros pedidos e forca somente itens ATIVOS. */
    public CarRentalOfferFilter activeOnly() {
        return new CarRentalOfferFilter(cityId, carCategory, transmission, minSeats, maxPricePerDay,
                EntityStatus.ACTIVE);
    }
}
