package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Preços já com margem; taxa de serviço e promoção entram na cotação. */
public record FlightSearchResult(
        String offerId,
        String provider,
        String companyName,
        UUID originCityId,
        UUID destinationCityId,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        String boardingAirport,
        String returnAirport,
        int passengers,
        BigDecimal pricePerPassenger,
        BigDecimal totalPrice,
        String currency,
        int seatsAvailable) {
}
