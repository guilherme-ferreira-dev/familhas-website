package br.com.familhas.search.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.BoardType;

/** totalPrice = quartos x noites, com margem. */
public record HotelSearchResult(
        String offerId,
        String provider,
        String hotelName,
        UUID cityId,
        int stars,
        String roomType,
        BoardType boardType,
        int maxGuests,
        int rooms,
        int nights,
        BigDecimal pricePerNight,
        BigDecimal totalPrice,
        String currency) {
}
