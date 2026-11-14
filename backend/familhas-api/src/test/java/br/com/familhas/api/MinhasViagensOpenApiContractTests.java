package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * O contrato de "Minhas viagens" em docs/api é consumido pela Loja: precisa ser um OpenAPI 3.1 válido,
 * com todos os $ref resolvidos, e tudo exige token (é sempre o dono do token).
 */
class MinhasViagensOpenApiContractTests {

    private static final Path SPEC = Path.of("../../docs/api/minhas-viagens.yaml");

    @Test
    void specIsValidOpenApiWithResolvedReferences() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI openApi = result.getOpenAPI();
        assertThat(openApi.getOpenapi()).isEqualTo("3.1.0");
        assertThat(openApi.getPaths()).containsOnlyKeys(
                "/api/me/trips",
                "/api/me/trips/{id}",
                "/api/me/favorites",
                "/api/me/favorites/{packageId}",
                "/api/me/ratings",
                "/api/me/ratings/{id}",
                "/api/me/recommendations");
    }

    @Test
    void everyOperationRequiresBearerToken() {
        OpenAPI openApi = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, null).getOpenAPI();

        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        openApi.getPaths().values().forEach(path -> path.readOperations()
                .forEach(operation -> assertThat(operation.getSecurity()).isNull()));
    }
}
