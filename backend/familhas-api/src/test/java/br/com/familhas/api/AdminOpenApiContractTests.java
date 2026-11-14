package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * O contrato do Admin (Fase 10, ADR 0011) é consumido pelo familhas-admin, que gera os tipos dele:
 * precisa ser um OpenAPI 3.1 válido, só com rotas /api/admin e sempre com bearer token.
 * Que toda rota /api/admin do código está no contrato é checado em AdminSecurityTests.
 */
class AdminOpenApiContractTests {

    static final Path SPEC = Path.of("../../docs/api/admin.yaml");

    static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }

    @Test
    void specIsValidOpenApiWithResolvedReferences() {
        OpenAPI openApi = parse();

        assertThat(openApi.getOpenapi()).isEqualTo("3.1.0");
        assertThat(openApi.getPaths()).containsKeys(
                "/api/admin/dashboard",
                "/api/admin/clients",
                "/api/admin/clients/segments",
                "/api/admin/bookings/{id}",
                "/api/admin/payments",
                "/api/admin/payments/export",
                "/api/admin/payments/stream",
                "/api/admin/quotes",
                "/api/admin/pricing-settings",
                "/api/admin/audit-logs");
    }

    @Test
    void everyPathIsAdminAndRequiresBearerToken() {
        OpenAPI openApi = parse();

        assertThat(openApi.getPaths().keySet()).allSatisfy(path -> assertThat(path).startsWith("/api/admin/"));
        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        openApi.getPaths().values().forEach(item -> item.readOperations()
                .forEach(operation -> assertThat(operation.getSecurity()).isNull()));
    }
}
