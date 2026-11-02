package br.com.familhas.rating.dto;

import java.util.UUID;

/** Filtros de GET /ratings. */
public record RatingFilter(
        UUID clientId,
        UUID travelPackageId) {

    public static RatingFilter empty() {
        return new RatingFilter(null, null);
    }
}
