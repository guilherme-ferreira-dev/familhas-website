package br.com.familhas.hotel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(HotelController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HotelControllerTests {

    private static final String VALID_BODY = """
            { "name": "Hotel", "cityId": "c1770000-0000-4000-8000-000000000002", "stars": 4, "status": "ACTIVE" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService hotelService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void adminListRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/hotels")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/hotels")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).findAll(any(), any());
    }

    @Test
    void adminListBindsFilters() throws Exception {
        when(hotelService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/hotels").param("minStars", "4")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminListRejectsStarsOutOfRange() throws Exception {
        mockMvc.perform(get("/api/admin/hotels").param("minStars", "6")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/hotels").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/hotels")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());

        verify(hotelService, never()).create(any());
    }

    @Test
    void createValidatesStars() throws Exception {
        mockMvc.perform(post("/api/admin/hotels")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"stars\": 4", "\"stars\": 0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stars").exists());
    }
}
