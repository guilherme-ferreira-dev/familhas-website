package br.com.familhas.favorite;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

/** Favoritos do cliente autenticado (/api/me/**: ROLE_USER ou ROLE_ADMIN). O cliente vem sempre do token. */
@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public FavoriteController(FavoriteService favoriteService, KeycloakClientSyncService keycloakClientSyncService) {
        this.favoriteService = favoriteService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    @GetMapping
    public List<PackageSummaryResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return favoriteService.findForClient(clientId(jwt));
    }

    @PutMapping("/{packageId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID packageId) {
        favoriteService.add(clientId(jwt), packageId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{packageId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID packageId) {
        favoriteService.remove(clientId(jwt), packageId);
        return ResponseEntity.noContent().build();
    }

    private UUID clientId(Jwt jwt) {
        return keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
    }
}
