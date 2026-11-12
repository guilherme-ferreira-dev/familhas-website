package br.com.familhas.me;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.ClientService;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.client.dto.ClientResponse;
import br.com.familhas.client.dto.MeResponse;
import br.com.familhas.client.dto.MeUpdateRequest;
import br.com.familhas.common.PageResponse;
import br.com.familhas.rating.RatingService;
import br.com.familhas.rating.dto.ClientRatingRequest;
import br.com.familhas.rating.dto.RatingResponse;

import jakarta.validation.Valid;

/**
 * Área do cliente autenticado. Tudo aqui é sempre "do dono do token":
 * o clientId vem do claim sub, nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final KeycloakClientSyncService keycloakClientSyncService;
    private final ClientService clientService;
    private final RatingService ratingService;

    public MeController(KeycloakClientSyncService keycloakClientSyncService, ClientService clientService,
            RatingService ratingService) {
        this.keycloakClientSyncService = keycloakClientSyncService;
        this.clientService = clientService;
        this.ratingService = ratingService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        UUID clientId = syncClient(jwt);
        ClientResponse client = clientService.findById(clientId);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .sorted()
                .toList();
        return new MeResponse(client.id(), client.name(), client.email(), client.phoneNumber(), client.status(),
                roles, client.favoriteIds(), client.myFlightIds());
    }

    @PutMapping
    public ResponseEntity<Void> update(@AuthenticationPrincipal Jwt jwt, @RequestBody @Valid MeUpdateRequest request) {
        clientService.updateProfile(syncClient(jwt), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ratings")
    public PageResponse<RatingResponse> ratings(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ratingService.findAllForClient(syncClient(jwt), pageable);
    }

    @PostMapping("/ratings")
    public ResponseEntity<RatingResponse> createRating(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid ClientRatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.createForClient(syncClient(jwt), request));
    }

    @PutMapping("/ratings/{id}")
    public RatingResponse updateRating(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody @Valid ClientRatingRequest request) {
        return ratingService.updateForClient(syncClient(jwt), id, request);
    }

    @DeleteMapping("/ratings/{id}")
    public ResponseEntity<Void> deleteRating(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        ratingService.deleteForClient(syncClient(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID syncClient(Jwt jwt) {
        return keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
    }
}
