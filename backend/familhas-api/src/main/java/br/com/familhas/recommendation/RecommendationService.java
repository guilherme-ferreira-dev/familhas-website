package br.com.familhas.recommendation;

import java.util.List;
import java.util.UUID;

import br.com.familhas.recommendation.dto.RecommendationResponse;

/**
 * Recomendação de pacotes para um cliente. Interface estável (guia, Fase 8; ADR 0010, D8): a v1 é por regras
 * ({@link RuleBasedRecommendationService}); uma v2 (ML ou embeddings) entra como outra implementação, mantendo
 * esta assinatura e o contrato GET /api/me/recommendations.
 */
public interface RecommendationService {

    int DEFAULT_LIMIT = 6;
    int MAX_LIMIT = 20;

    /**
     * Até {@code limit} pacotes, do mais para o menos relevante. Nunca sugere pacote já reservado, favoritado ou
     * avaliado pelo cliente, nem pacote inativo ou com embarque passado. Pode devolver lista vazia.
     */
    List<RecommendationResponse> recommend(UUID clientId, int limit);
}
