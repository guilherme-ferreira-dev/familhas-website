package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.Transmission;

/** totalPrice = dias, com margem. */
public record CarSearchResult(
        String offerId,
        String provider,
        UUID cityId,
        String companyName,
        String carModel,
        CarCategory carCategory,
        Transmission transmission,
        int seats,
        int days,
        BigDecimal pricePerDay,
        BigDecimal totalPrice,
        String currency) {
}
