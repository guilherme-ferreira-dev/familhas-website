package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pacote na vitrine: preço "a partir de" por pessoa, cadastrado pelo Enéas (com promoção, quando houver).
 * O preço final sai da cotação, calculado pelos componentes reais (ADR 0006).
 */
public record PackageSearchResult(
        UUID id,
        String title,
        String imageUrl,
        UUID categoryId,
        UUID originCityId,
        UUID destinationCityId,
        UUID flightId,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        int passengers,
        BigDecimal pricePerPerson,
        BigDecimal originalPricePerPerson,
        BigDecimal totalPrice,
        String currency,
        int seatsAvailable) {
}
