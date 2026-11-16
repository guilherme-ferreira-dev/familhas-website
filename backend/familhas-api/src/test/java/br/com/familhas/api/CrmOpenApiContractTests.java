package br.com.familhas.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * docs/api/crm.yaml (ADR 0014) é a fonte dos tipos do CRM no familhas-admin: OpenAPI 3.1 válido, só /api/crm,
 * bearer em tudo menos o link assinado, e toda rota /api/crm do código documentada com o método certo.
 */
@SpringBootTest
@ActiveProfiles("test")
class CrmOpenApiContractTests {

    static final Path SPEC = Path.of("../../docs/api/crm.yaml");

    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @MockitoBean private JwtDecoder jwtDecoder;

    static OpenAPI parse() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readLocation(SPEC.toUri().toString(), null, options);
        assertThat(result.getMessages()).isEmpty();
        return result.getOpenAPI();
    }

    @Test
    void specIsValidAndOnlyTheSignedContentIsPublic() {
        OpenAPI openApi = parse();
        assertThat(openApi.getOpenapi()).isEqualTo("3.1.0");
        assertThat(openApi.getPaths().keySet()).allSatisfy(path -> assertThat(path).startsWith("/api/crm/"));
        assertThat(openApi.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey("bearerAuth"));
        openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
            if (path.equals("/api/crm/files/{id}/content")) {
                assertThat(operation.getSecurity()).isEmpty();
            } else {
                assertThat(operation.getSecurity()).as(path).isNull();
            }
        }));
    }

    @Test
    void everyCrmRouteIsInTheContract() {
        OpenAPI openApi = parse();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/crm")) {
                    continue;
                }
                assertThat(openApi.getPaths()).as("rota %s em docs/api/crm.yaml", pattern).containsKey(pattern);
                Set<String> documented = Set.copyOf(openApi.getPaths().get(pattern).readOperationsMap().keySet()
                        .stream().map(Enum::name).toList());
                info.getMethodsCondition().getMethods().forEach(method -> assertThat(documented)
                        .as("metodo %s de %s", method, pattern).contains(method.name()));
            }
        });
    }
}
