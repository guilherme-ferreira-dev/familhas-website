package br.com.familhas.carRental.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.Transmission;

public record CarRentalOfferResponse(
        UUID id,
        UUID cityId,
        String companyName,
        String carModel,
        CarCategory carCategory,
        Transmission transmission,
        Integer seats,
        BigDecimal pricePerDay,
        String currency,
        EntityStatus status) {
}
