package br.com.familhas.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(RecommendationController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class RecommendationControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RecommendationService recommendationService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1")).authorities(new SimpleGrantedAuthority(role)));
    }

    @Test
    void requiresTokenAndUserRole() throws Exception {
        mockMvc.perform(get("/api/me/recommendations")).andExpect(status().isUnauthorized());
        mockMvc.perform(as("ROLE_OTHER", get("/api/me/recommendations"))).andExpect(status().isForbidden());
        verify(recommendationService, never()).recommend(any(), anyInt());
    }

    @Test
    void usesTheClientFromTheTokenAndDefaultLimit() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(recommendationService.recommend(CLIENT, 6)).thenReturn(List.of());

        mockMvc.perform(as("ROLE_USER", get("/api/me/recommendations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        verify(recommendationService).recommend(CLIENT, 6);
    }

    @Test
    void limitOutOfRangeIsBadRequestProblem() throws Exception {
        mockMvc.perform(as("ROLE_USER", get("/api/me/recommendations").param("limit", "21")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(as("ROLE_USER", get("/api/me/recommendations").param("limit", "0")))
                .andExpect(status().isBadRequest());
        verify(recommendationService, never()).recommend(any(), anyInt());
    }
}
