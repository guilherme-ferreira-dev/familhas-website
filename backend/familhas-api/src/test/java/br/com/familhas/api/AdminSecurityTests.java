package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * Guia, Fase 10: cliente comum recebe 403 em /api/admin/**.
 *
 * Varre todas as rotas /api/admin registradas no Spring MVC (inclusive as que entrarem depois) e chama cada
 * uma sem token (401), com token de cliente comum (ROLE_USER: 403) e com token sem papel (403). A
 * autorização acontece no filtro, antes do controller: nenhum service nem banco é tocado.
 *
 * Também garante que toda rota /api/admin do código está documentada em docs/api/admin.yaml.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AdminSecurityTests {

    /**
     * Rotas admin que existem desde a Fase 2, mas que o familhas-admin não usa: fora do contrato.
     * Cidades e categorias só aparecem no Admin como opções de seleção (GET da lista).
     */
    private static final Set<String> NOT_IN_CONTRACT_PATHS = Set.of(
            "/api/admin/ratings", "/api/admin/ratings/{id}",
            "/api/admin/cities/{id}", "/api/admin/categories/{id}");
    private static final Set<String> NOT_IN_CONTRACT_ROUTES = Set.of(
            "POST /api/admin/cities", "POST /api/admin/categories");

    record Route(HttpMethod method, String pattern) {
        String uri() {
            return pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void anonymousGets401OnEveryAdminRoute() throws Exception {
        for (Route route : adminRoutes()) {
            mockMvc.perform(request(route.method(), route.uri()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void regularClientGets403OnEveryAdminRoute() throws Exception {
        for (Route route : adminRoutes()) {
            mockMvc.perform(request(route.method(), route.uri())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void tokenWithoutRolesGets403OnEveryAdminRoute() throws Exception {
        for (Route route : adminRoutes()) {
            mockMvc.perform(request(route.method(), route.uri()).with(jwt().authorities(List.of()))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminRoutesOfThisPhaseAreScanned() {
        assertThat(adminRoutes()).extracting(Route::pattern).contains(
                "/api/admin/clients",
                "/api/admin/flights/{id}");
    }

    @Test
    void everyAdminRouteIsInTheContract() {
        OpenAPI openApi = AdminOpenApiContractTests.parse();
        for (Route route : adminRoutes()) {
            if (NOT_IN_CONTRACT_PATHS.contains(route.pattern())
                    || NOT_IN_CONTRACT_ROUTES.contains(route.method().name() + " " + route.pattern())) {
                continue;
            }
            assertThat(openApi.getPaths()).as("rota %s %s em docs/api/admin.yaml", route.method(), route.pattern())
                    .containsKey(route.pattern());
            assertThat(openApi.getPaths().get(route.pattern()).readOperationsMap().keySet())
                    .extracting(Enum::name)
                    .as("metodo %s de %s em docs/api/admin.yaml", route.method(), route.pattern())
                    .contains(route.method().name());
        }
    }

    private List<Route> adminRoutes() {
        List<Route> routes = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet().forEach((RequestMappingInfo info) -> {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/admin")) {
                    continue;
                }
                if (methods.isEmpty()) {
                    routes.add(new Route(HttpMethod.GET, pattern));
                }
                methods.forEach(m -> routes.add(new Route(HttpMethod.valueOf(m.name()), pattern)));
            }
        });
        assertThat(routes).isNotEmpty();
        return routes;
    }
}
