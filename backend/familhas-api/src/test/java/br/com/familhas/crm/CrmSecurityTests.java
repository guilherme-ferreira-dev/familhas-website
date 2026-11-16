package br.com.familhas.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.nimbusds.jwt.JWTClaimsSet;
import br.com.familhas.config.SecurityConfig;
import com.nimbusds.jwt.PlainJWT;

/**
 * Prompt mestre, Etapa 1 (ADR 0013): só a equipe do realm do CRM entra em /api/crm/**.
 *
 * A primeira parte varre todas as rotas /api/crm registradas (inclusive as que entrarem nas próximas etapas):
 * anônimo 401, cliente e admin da Loja 403, token sem papel 403. A segunda passa pela cadeia real de
 * autenticação: o {@code iss} do token escolhe o decoder (mockado aqui) e o conversor de papéis de cada realm.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CrmSecurityTests {

    record Route(HttpMethod method, String pattern) {
        String uri() {
            return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @MockitoBean private JwtDecoder storeDecoder;
    @MockitoBean private CrmJwtDecoder crmDecoder;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private String storeIssuer;
    @Value("${familhas.security.crm.issuer-uri}") private String crmIssuer;

    // --- todas as rotas /api/crm ---

    @Test
    void anonymousGets401OnEveryCrmRoute() throws Exception {
        for (Route route : crmRoutes()) {
            mockMvc.perform(request(route.method(), route.uri()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void storeClientAndStoreAdminGet403OnEveryCrmRoute() throws Exception {
        for (String role : List.of("ROLE_USER", "ROLE_ADMIN")) {
            for (Route route : crmRoutes()) {
                mockMvc.perform(request(route.method(), route.uri())
                                .with(jwt().authorities(new SimpleGrantedAuthority(role)))
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void tokenWithoutRolesGets403OnEveryCrmRoute() throws Exception {
        for (Route route : crmRoutes()) {
            mockMvc.perform(request(route.method(), route.uri()).with(jwt().authorities(List.of()))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // --- cadeia real: issuer -> decoder -> papéis ---

    @Test
    void crmTokenGetsProfileAndPermissions() throws Exception {
        String token = token(crmIssuer, "crm-atendente");
        when(crmDecoder.decode(token)).thenReturn(decoded(token, crmIssuer, "crm-atendente"));

        mockMvc.perform(get("/api/crm/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("equipe.teste"))
                .andExpect(jsonPath("$.roles[0]").value("ATENDENTE"))
                .andExpect(jsonPath("$.permissions").isArray());
        verify(storeDecoder, never()).decode(anyString());
    }

    @Test
    void crmNonAdminCannotUseStoreAdminRoutesNorActuator() throws Exception {
        for (String role : List.of("crm-gestor", "crm-atendente", "crm-marketing")) {
            String token = token(crmIssuer, role);
            when(crmDecoder.decode(token)).thenReturn(decoded(token, crmIssuer, role));
            mockMvc.perform(get("/api/admin/clients").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void crmAdminKeepsAccessToStoreAdministration() throws Exception {
        String token = token(crmIssuer, "crm-admin");
        when(crmDecoder.decode(token)).thenReturn(decoded(token, crmIssuer, "crm-admin"));
        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void storeTokenNeverReachesTheCrmEvenWithAForgedCrmRole() throws Exception {
        String token = token(storeIssuer, "ROLE_ADMIN", "ROLE_CRM_ADMIN", "crm-admin");
        when(storeDecoder.decode(token)).thenReturn(decoded(token, storeIssuer, "ROLE_ADMIN", "ROLE_CRM_ADMIN", "crm-admin"));

        mockMvc.perform(get("/api/crm/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        verify(crmDecoder, never()).decode(anyString());
    }

    @Test
    void unknownIssuerIs401WithoutCallingAnyDecoder() throws Exception {
        String token = token("https://evil.example/realms/voe-familhas-crm", "crm-admin");
        mockMvc.perform(get("/api/crm/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verify(crmDecoder, never()).decode(anyString());
        verify(storeDecoder, never()).decode(anyString());
    }

    @Test
    void invalidOrExpiredCrmTokenIs401() throws Exception {
        String token = token(crmIssuer, "crm-admin");
        when(crmDecoder.decode(token)).thenThrow(new BadJwtException("Jwt expired"));
        mockMvc.perform(get("/api/crm/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedTokenIs401() throws Exception {
        mockMvc.perform(get("/api/crm/me").header("Authorization", "Bearer nao-e-um-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedFileContentNeedsAValidSignature() throws Exception {
        mockMvc.perform(get("/api/crm/files/{id}/content", UUID.randomUUID()).param("expires", "4102444800")
                        .param("signature", "forjada"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_LINK_INVALID"));
        mockMvc.perform(get("/api/crm/files/{id}/content", UUID.randomUUID())).andExpect(status().isBadRequest());
    }

    private static String token(String issuer, String... roles) {
        return new PlainJWT(new JWTClaimsSet.Builder().issuer(issuer).subject("sub-" + UUID.randomUUID())
                .claim("realm_access", Map.of("roles", List.of(roles))).build()).serialize();
    }

    private static Jwt decoded(String token, String issuer, String... roles) {
        return Jwt.withTokenValue(token).header("alg", "RS256").issuer(issuer).subject("sub-1")
                .claim("preferred_username", "equipe.teste").claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    private List<Route> crmRoutes() {
        List<Route> routes = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet().forEach((RequestMappingInfo info) -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                // Unica rota publica do CRM: coberta por signedFileContentNeedsAValidSignature.
                if (!pattern.startsWith("/api/crm") || pattern.equals(SecurityConfig.CRM_SIGNED_FILE_CONTENT)) {
                    continue;
                }
                if (methods.isEmpty()) {
                    routes.add(new Route(HttpMethod.GET, pattern));
                }
                methods.forEach(m -> routes.add(new Route(HttpMethod.valueOf(m.name()), pattern)));
            }
        });
        assertThat(routes).extracting(Route::pattern).contains("/api/crm/me");
        return routes;
    }
}
