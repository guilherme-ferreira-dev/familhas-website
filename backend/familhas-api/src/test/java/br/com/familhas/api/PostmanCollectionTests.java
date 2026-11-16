package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * A coleção Postman (docs/postman, guia Fase 11) não pode envelhecer em silêncio: toda requisição tem que bater
 * numa rota registrada no Spring MVC, e todo query param tem que estar declarado no contrato da rota (quando a
 * rota está em docs/api). Paginação (page, size, sort) vale para toda lista (ADR 0003).
 */
@SpringBootTest
@ActiveProfiles("test")
class PostmanCollectionTests {

    static final Path COLLECTION = Path.of("../../docs/postman/familhas-api.postman_collection.json");
    static final List<String> CONTRACTS = List.of("familhas-api.yaml", "checkout.yaml", "minhas-viagens.yaml",
            "admin.yaml");
    static final Set<String> PAGINATION = Set.of("page", "size", "sort");
    /** Fora do Spring MVC da aplicação: Actuator e springdoc têm mapeamentos próprios. */
    static final Set<String> PLATFORM_PATHS = Set.of("/actuator/health", "/actuator/prometheus",
            "/v3/api-docs/public", "/v3/api-docs/admin");

    record PostmanRequest(String folder, String name, String method, String path, List<String> query) {
    }

    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void everyRequestHitsARegisteredRoute() throws Exception {
        List<PostmanRequest> requests = requests();
        assertThat(requests).hasSizeGreaterThan(30);

        for (PostmanRequest request : requests) {
            if (PLATFORM_PATHS.contains(request.path())) {
                continue;
            }
            MockHttpServletRequest servletRequest = new MockHttpServletRequest(request.method(),
                    request.path().replaceAll("\\{\\{[^}]+}}", UUID.randomUUID().toString()));
            assertThat(handlerMapping.getHandler(servletRequest))
                    .as("%s / %s: %s %s", request.folder(), request.name(), request.method(), request.path())
                    .isNotNull();
        }
    }

    @Test
    void queryParametersAreDeclaredInTheContracts() throws Exception {
        List<OpenAPI> contracts = new ArrayList<>();
        for (String contract : CONTRACTS) {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setResolveFully(true);
            contracts.add(new OpenAPIParser().readLocation(Path.of("../../docs/api/" + contract).toUri().toString(),
                    null, options).getOpenAPI());
        }

        for (PostmanRequest request : requests()) {
            String templated = request.path().replaceAll("\\{\\{[^}]+}}", "{id}");
            Operation operation = findOperation(contracts, templated, request.method());
            if (operation == null) {
                continue;
            }
            Set<String> declared = new HashSet<>(PAGINATION);
            if (operation.getParameters() != null) {
                operation.getParameters().stream().filter(p -> "query".equals(p.getIn())).map(Parameter::getName)
                        .forEach(declared::add);
            }
            assertThat(declared).as("query params de %s %s", request.method(), request.path())
                    .containsAll(request.query());
        }
    }

    @Test
    void clientAndAdminFoldersUseTheRightKeycloakClients() throws Exception {
        JsonNode collection = new ObjectMapper().readTree(COLLECTION.toFile());
        for (JsonNode folder : collection.path("item")) {
            String name = folder.path("name").asText();
            String clientId = oauthValue(folder, "clientId");
            if (name.contains("Cliente")) {
                assertThat(clientId).isEqualTo("familhas-web");
            } else if (name.contains("Admin")) {
                assertThat(clientId).isEqualTo("familhas-admin");
            } else {
                assertThat(folder.at("/auth/type").asText()).isEqualTo("noauth");
            }
            if (clientId != null) {
                assertThat(oauthValue(folder, "grant_type")).isEqualTo("authorization_code_with_pkce");
            }
        }
        // Nenhum segredo: clients públicos com PKCE, sem client secret nem senha salva.
        String raw = java.nio.file.Files.readString(COLLECTION);
        assertThat(raw).doesNotContain("clientSecret", "admin1234\"", "user12345\"", "password\"");
    }

    private static String oauthValue(JsonNode folder, String key) {
        for (JsonNode entry : folder.at("/auth/oauth2")) {
            if (key.equals(entry.path("key").asText())) {
                return entry.path("value").asText();
            }
        }
        return null;
    }

    private static Operation findOperation(List<OpenAPI> contracts, String path, String method) {
        for (OpenAPI contract : contracts) {
            for (var entry : contract.getPaths().entrySet()) {
                if (entry.getKey().replaceAll("\\{[^}]+}", "{id}").equals(path)) {
                    PathItem item = entry.getValue();
                    return item.readOperationsMap().get(PathItem.HttpMethod.valueOf(method));
                }
            }
        }
        return null;
    }

    static List<PostmanRequest> requests() throws IOException {
        JsonNode collection = new ObjectMapper().readTree(COLLECTION.toFile());
        List<PostmanRequest> requests = new ArrayList<>();
        for (JsonNode folder : collection.path("item")) {
            for (JsonNode item : folder.path("item")) {
                JsonNode url = item.at("/request/url");
                List<String> query = new ArrayList<>();
                url.path("query").forEach(q -> query.add(q.path("key").asText()));
                requests.add(new PostmanRequest(folder.path("name").asText(), item.path("name").asText(),
                        item.at("/request/method").asText(), "/" + String.join("/", toList(url.path("path"))),
                        query));
            }
        }
        return requests;
    }

    private static List<String> toList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
