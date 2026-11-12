package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * O contrato de cotação, reserva e pagamento em docs/api é consumido pela Loja (Fase 7):
 * precisa ser um OpenAPI 3.1 válido, com todos os $ref resolvidos.
 */
class CheckoutOpenApiContractTests {

    private static final Path SPEC = Path.of("../../docs/api/checkout.yaml");

    @Test
    void specIsValidOpenApiWithResolvedReferences() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, options);

        assertThat(result.getMessages()).isEmpty();
        OpenAPI openApi = result.getOpenAPI();
        assertThat(openApi.getOpenapi()).isEqualTo("3.1.0");
        assertThat(openApi.getPaths()).containsKeys(
                "/api/me/quotes",
                "/api/me/quotes/{id}",
                "/api/me/bookings",
                "/api/me/bookings/{id}",
                "/api/me/bookings/{id}/checkout-session",
                "/api/webhooks/stripe");
    }

    @Test
    void webhookIsPublicAndEverythingElseRequiresBearerToken() {
        OpenAPI openApi = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, null).getOpenAPI();

        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        assertThat(openApi.getPaths().get("/api/webhooks/stripe").getPost().getSecurity()).isEmpty();
    }
}
