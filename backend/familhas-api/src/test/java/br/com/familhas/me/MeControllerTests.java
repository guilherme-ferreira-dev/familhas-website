package br.com.familhas.me;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.client.ClientService;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.client.dto.ClientResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.rating.RatingService;
import br.com.familhas.rating.dto.RatingResponse;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class MeControllerTests {

    private static final UUID CLIENT_ID = UUID.fromString("c11e0000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean
    private ClientService clientService;
    @MockitoBean
    private RatingService ratingService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void meWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsSyncedProfileAndRoles() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT_ID);
        when(clientService.findById(CLIENT_ID)).thenReturn(new ClientResponse(
                CLIENT_ID, "Ana", "ana@familhas.com", "+55", EntityStatus.ACTIVE, true, List.of(), List.of()));

        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject("sub-1").claim("email", "ana@familhas.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CLIENT_ID.toString()))
                .andExpect(jsonPath("$.email").value("ana@familhas.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void clientRatingUsesClientIdFromTokenAndIgnoresBody() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT_ID);
        when(ratingService.createForClient(eq(CLIENT_ID), any())).thenReturn(new RatingResponse(
                UUID.randomUUID(), 5, CLIENT_ID, UUID.randomUUID()));

        String body = """
                { "rating": 5, "travelPackageId": "bac00000-0000-4000-8000-000000000001",
                  "clientId": "c11e0000-0000-4000-8000-000000000002" }
                """;

        mockMvc.perform(post("/api/me/ratings")
                        .with(jwt().jwt(j -> j.subject("sub-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID.toString()));
    }

    @Test
    void adminAreaIsNotReachableFromMeRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/clients")
                        .with(jwt().jwt(j -> j.subject("sub-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(clientService, never()).findAll(any(), any());
    }
}
