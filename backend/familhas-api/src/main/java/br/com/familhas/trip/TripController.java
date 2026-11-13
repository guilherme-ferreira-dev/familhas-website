package br.com.familhas.trip;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.common.PageResponse;
import br.com.familhas.trip.dto.TripResponse;

/** "Minhas viagens" do cliente autenticado (/api/me/**: ROLE_USER ou ROLE_ADMIN). O cliente vem sempre do token. */
@RestController
@RequestMapping("/api/me/trips")
public class TripController {

    private final TripService tripService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public TripController(TripService tripService, KeycloakClientSyncService keycloakClientSyncService) {
        this.tripService = tripService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    /** Futuras em ordem de início; passadas da mais recente para a mais antiga. Sem ordenação configurável. */
    @GetMapping
    public PageResponse<TripResponse> findAll(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "UPCOMING") TripTiming when,
            @PageableDefault(size = 20) Pageable pageable) {
        return tripService.findForClient(clientId(jwt), when, pageable);
    }

    @GetMapping("/{id}")
    public TripResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return tripService.findOneForClient(clientId(jwt), id);
    }

    private UUID clientId(Jwt jwt) {
        return keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
    }
}
