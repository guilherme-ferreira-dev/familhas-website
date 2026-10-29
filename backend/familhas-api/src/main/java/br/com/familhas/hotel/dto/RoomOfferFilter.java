package br.com.familhas.hotel.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

/**
 * Filtros de GET /room-offers. guests: quartos que comportam ao menos N hóspedes.
 * maxPricePerNight compara o valor na moeda da própria oferta (sem conversão de câmbio).
 */
public record RoomOfferFilter(
        UUID hotelId,
        UUID cityId,
        BoardType boardType,
        @Min(1) Integer guests,
        @DecimalMin("0.0") BigDecimal maxPricePerNight,
        EntityStatus status) {

    public static RoomOfferFilter empty() {
        return new RoomOfferFilter(null, null, null, null, null, null);
    }
}
