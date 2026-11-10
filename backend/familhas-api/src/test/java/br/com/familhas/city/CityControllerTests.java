package br.com.familhas.city;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.city.dto.CityResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.enums.CityType;
import br.com.familhas.enums.EntityStatus;

@WebMvcTest(CityController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CityControllerTests {

    private static final UUID ID = UUID.fromString("c1770000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CityService cityService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void findByIdAcceptsUuidPathVariableForAdmin() throws Exception {
        when(cityService.findById(ID))
                .thenReturn(new CityResponse(ID, "Sao Paulo", "Brasil", CityType.ORIGIN, EntityStatus.ACTIVE));

        mockMvc.perform(get("/api/admin/cities/{id}", ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("Sao Paulo"));
    }

    @Test
    void findByIdRejectsLegacyNumericId() throws Exception {
        mockMvc.perform(get("/api/admin/cities/{id}", "1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());

        verify(cityService, never()).findById(any());
    }

    @Test
    void adminRouteWithoutTokenReturns401AsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/admin/cities"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void adminRouteWithUserRoleReturns403AsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/admin/cities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verify(cityService, never()).findAll(any(), any());
    }

    @Test
    void corsAllowsConfiguredOriginAndBlocksUnknownOne() throws Exception {
        mockMvc.perform(options("/api/admin/cities")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/api/admin/cities")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
