package br.com.familhas.hotel.dto;

import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

public record HotelResponse(
        UUID id,
        String name,
        UUID cityId,
        String address,
        Integer stars,
        String description,
        String imageUrl,
        EntityStatus status) {
}
