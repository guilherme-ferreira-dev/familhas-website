package br.com.familhas.recommendation;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.recommendation.dto.RecommendationResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Recomendações para o cliente autenticado (/api/me/**: ROLE_USER ou ROLE_ADMIN). O cliente vem do token. */
@RestController
@RequestMapping("/api/me/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public RecommendationController(RecommendationService recommendationService,
            KeycloakClientSyncService keycloakClientSyncService) {
        this.recommendationService = recommendationService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    @GetMapping
    public List<RecommendationResponse> recommend(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "" + RecommendationService.DEFAULT_LIMIT)
            @Min(1) @Max(RecommendationService.MAX_LIMIT) int limit) {
        UUID clientId = keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
        return recommendationService.recommend(clientId, limit);
    }
}
