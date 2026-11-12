package br.com.familhas.booking;

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

import br.com.familhas.booking.dto.BookingRequest;
import br.com.familhas.booking.dto.BookingResponse;
import br.com.familhas.booking.dto.BookingSummaryResponse;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;

/** Reservas do cliente autenticado (/api/me/**: ROLE_USER ou ROLE_ADMIN). O cliente vem sempre do token. */
@RestController
@RequestMapping("/api/me/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final KeycloakClientSyncService keycloakClientSyncService;

    public BookingController(BookingService bookingService, KeycloakClientSyncService keycloakClientSyncService) {
        this.bookingService = bookingService;
        this.keycloakClientSyncService = keycloakClientSyncService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestBody @Valid BookingRequest request) {
        BookingResponse booking = bookingService.create(clientId(jwt), request);
        return ResponseEntity.created(URI.create("/api/me/bookings/" + booking.id())).body(booking);
    }

    @GetMapping
    public PageResponse<BookingSummaryResponse> findAll(@AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.findAllForClient(clientId(jwt), pageable);
    }

    @GetMapping("/{id}")
    public BookingResponse findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookingService.findForClient(clientId(jwt), id);
    }

    private UUID clientId(Jwt jwt) {
        return keycloakClientSyncService.syncClientId(KeycloakClientSyncService.KeycloakUser.fromJwt(jwt));
    }
}
