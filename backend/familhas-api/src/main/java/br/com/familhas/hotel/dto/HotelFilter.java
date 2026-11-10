package br.com.familhas.hotel.dto;

import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Filtros de GET /hotels. minStars: classificação mínima (1 a 5). */
public record HotelFilter(
        UUID cityId,
        @Min(1)
        @Max(5) Integer minStars,
        EntityStatus status,
        @Size(max = 100) String q) {

    public static HotelFilter empty() {
        return new HotelFilter(null, null, null, null);
    }

    /** Catalogo publico: mantem os filtros pedidos e forca somente itens ATIVOS. */
    public HotelFilter activeOnly() {
        return new HotelFilter(cityId, minStars, EntityStatus.ACTIVE, q);
    }
}
