package br.com.familhas.trip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(TripController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class TripControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    private static final UUID TRIP = UUID.fromString("b00c0000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TripService tripService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1").claim("email", "ana@exemplo.com"))
                .authorities(new SimpleGrantedAuthority(role)));
    }

    @Test
    void tripsRequireToken() throws Exception {
        mockMvc.perform(get("/api/me/trips")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/trips/" + TRIP)).andExpect(status().isUnauthorized());
        verify(tripService, never()).findForClient(any(), any(), any());
    }

    @Test
    void tripsRequireUserRole() throws Exception {
        mockMvc.perform(as("ROLE_OTHER", get("/api/me/trips"))).andExpect(status().isForbidden());
        verify(tripService, never()).findForClient(any(), any(), any());
    }

    @Test
    void listsUpcomingByDefaultForTheClientFromTheToken() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(tripService.findForClient(eq(CLIENT), eq(TripTiming.UPCOMING), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(as("ROLE_USER", get("/api/me/trips")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listsPastTrips() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(tripService.findForClient(eq(CLIENT), eq(TripTiming.PAST), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(as("ROLE_USER", get("/api/me/trips").param("when", "PAST")))
                .andExpect(status().isOk());
        verify(tripService).findForClient(eq(CLIENT), eq(TripTiming.PAST), any(Pageable.class));
    }

    @Test
    void invalidTimingIsBadRequestProblem() throws Exception {
        mockMvc.perform(as("ROLE_USER", get("/api/me/trips").param("when", "SOMEDAY")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void tripOfAnotherClientIs404ProblemWithCode() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(tripService.findOneForClient(CLIENT, TRIP)).thenThrow(TripService.notFound(TRIP));

        mockMvc.perform(as("ROLE_USER", get("/api/me/trips/" + TRIP)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
    }
}
