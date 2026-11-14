package br.com.familhas.rating.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

/** Avaliação vista pelo próprio cliente (/api/me/ratings): o pacote vem resumido, o clientId é implícito. */
public record MyRatingResponse(
        UUID id,
        int rating,
        PackageSummaryResponse travelPackage,
        Instant createdAt,
        Instant updatedAt) {
}
