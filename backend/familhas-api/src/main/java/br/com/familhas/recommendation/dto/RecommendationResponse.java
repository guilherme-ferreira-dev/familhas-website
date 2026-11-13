package br.com.familhas.recommendation.dto;

import java.util.List;

import br.com.familhas.recommendation.RecommendationReason;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

/** Pacote recomendado, com a pontuação (só para ordenar) e os motivos. */
public record RecommendationResponse(
        PackageSummaryResponse travelPackage,
        double score,
        List<RecommendationReason> reasons) {
}
