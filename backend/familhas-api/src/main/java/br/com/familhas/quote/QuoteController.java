package br.com.familhas.quote;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.common.PageResponse;
import br.com.familhas.quote.dto.QuoteRequest;
import br.com.familhas.quote.dto.QuoteResponse;
import br.com.familhas.quote.dto.QuoteSummaryResponse;
import jakarta.validation.Valid;

/** Cotações do cliente autenticado (/api/me/**: ROLE_USER ou ROLE_ADMIN). O cliente vem sempre do token. */
@RestController
@RequestMapping("/api/me/quotes")
public class QuoteController {

    private final QuoteService quoteService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public QuoteController(QuoteService quoteService, KeycloakClientSyncService keycloakClientSyncService) {
        this.quoteService = quoteService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid QuoteRequest request) {
        QuoteResponse quote = quoteService.create(clientId(jwt), request);
        return ResponseEntity.created(URI.create("/api/me/quotes/" + quote.id())).body(quote);
    }

    @GetMapping
    public PageResponse<QuoteSummaryResponse> findAll(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return quoteService.findAllForClient(clientId(jwt), pageable);
    }

    @GetMapping("/{id}")
    public QuoteResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return quoteService.findForClient(clientId(jwt), id);
    }

    private UUID clientId(Jwt jwt) {
        return keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
    }
}
