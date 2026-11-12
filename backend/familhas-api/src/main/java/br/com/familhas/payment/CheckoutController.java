package br.com.familhas.payment;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.payment.dto.CheckoutSessionRequest;
import br.com.familhas.payment.dto.CheckoutSessionResponse;
import jakarta.validation.Valid;

/** Checkout da reserva do cliente autenticado (docs/api/checkout.yaml). 201 cria, 200 reaproveita. */
@RestController
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public CheckoutController(CheckoutService checkoutService, KeycloakClientSyncService keycloakClientSyncService) {
        this.checkoutService = checkoutService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    @PostMapping("/api/me/bookings/{id}/checkout-session")
    public ResponseEntity<CheckoutSessionResponse> open(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody @Valid CheckoutSessionRequest request) {
        UUID clientId = keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
        CheckoutService.CheckoutResult result = checkoutService.open(clientId, id, request.paymentMethod());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }
}
