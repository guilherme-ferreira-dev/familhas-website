package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guia, Fase 11: OpenAPI por grupo. O grupo public não pode vazar rota admin; o admin só tem /api/admin; os dois
 * declaram o bearer token. A Swagger UI abre sem token.
 */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GeneratedOpenApiGroupsTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void publicGroupHasStoreRoutesAndNoAdminRoute() throws Exception {
        List<String> paths = paths("public");

        assertThat(paths).contains("/api/travel-packages", "/api/search/packages", "/api/me/bookings",
                "/api/me/trips", "/api/webhooks/stripe");
        assertThat(paths).noneMatch(path -> path.startsWith("/api/admin"));
    }

    @Test
    void adminGroupHasOnlyAdminRoutes() throws Exception {
        List<String> paths = paths("admin");

        assertThat(paths).contains("/api/admin/payments", "/api/admin/dashboard", "/api/admin/audit-logs");
        assertThat(paths).allMatch(path -> path.startsWith("/api/admin/"));
    }

    @Test
    void bothGroupsDeclareTheBearerScheme() throws Exception {
        for (String group : List.of("public", "admin")) {
            JsonNode doc = document(group);
            assertThat(doc.at("/components/securitySchemes/bearer-jwt/scheme").asText()).isEqualTo("bearer");
            assertThat(doc.at("/info/title").asText()).isEqualTo("Familhas API");
        }
    }

    @Test
    void swaggerUiIsReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    private List<String> paths(String group) throws Exception {
        List<String> paths = new ArrayList<>();
        document(group).path("paths").fieldNames().forEachRemaining(paths::add);
        return paths;
    }

    private JsonNode document(String group) throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs/" + group))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }
}
