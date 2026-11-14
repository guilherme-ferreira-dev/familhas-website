package br.com.familhas.favorite;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(FavoriteController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class FavoriteControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    private static final UUID PACKAGE = UUID.fromString("bac00000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FavoriteService favoriteService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1")).authorities(new SimpleGrantedAuthority(role)));
    }

    @Test
    void favoritesRequireTokenAndUserRole() throws Exception {
        mockMvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me/favorites/" + PACKAGE)).andExpect(status().isUnauthorized());
        mockMvc.perform(as("ROLE_OTHER", put("/api/me/favorites/" + PACKAGE))).andExpect(status().isForbidden());
        verify(favoriteService, never()).add(any(), any());
    }

    @Test
    void addAndRemoveUseTheClientFromTheToken() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);

        mockMvc.perform(as("ROLE_USER", put("/api/me/favorites/" + PACKAGE))).andExpect(status().isNoContent());
        mockMvc.perform(as("ROLE_USER", delete("/api/me/favorites/" + PACKAGE))).andExpect(status().isNoContent());

        verify(favoriteService).add(CLIENT, PACKAGE);
        verify(favoriteService).remove(CLIENT, PACKAGE);
    }

    @Test
    void listReturnsArray() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(favoriteService.findForClient(CLIENT)).thenReturn(List.of());

        mockMvc.perform(as("ROLE_USER", get("/api/me/favorites")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void unknownPackageIs404WithCode() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        doThrow(FavoriteService.packageNotFound(PACKAGE)).when(favoriteService).add(CLIENT, PACKAGE);

        mockMvc.perform(as("ROLE_USER", put("/api/me/favorites/" + PACKAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));
    }
}
