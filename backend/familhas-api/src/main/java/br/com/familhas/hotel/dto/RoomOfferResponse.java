package br.com.familhas.hotel.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.EntityStatus;

public record RoomOfferResponse(
        UUID id,
        UUID hotelId,
        String roomType,
        BoardType boardType,
        Integer maxGuests,
        BigDecimal pricePerNight,
        String currency,
        EntityStatus status) {
}
