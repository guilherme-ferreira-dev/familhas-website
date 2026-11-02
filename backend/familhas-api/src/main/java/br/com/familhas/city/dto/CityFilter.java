package br.com.familhas.city.dto;

import br.com.familhas.enums.CityType;
import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.Size;

/** Filtros de GET /cities. q busca no nome. */
public record CityFilter(
        CityType cityType,
        EntityStatus status,
        @Size(max = 100) String q) {

    public static CityFilter empty() {
        return new CityFilter(null, null, null);
    }
}
