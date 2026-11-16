package br.com.familhas.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI gerado do código, em dois grupos (guia, Fase 11; ADR 0012):
 *   public  Loja: catálogo, busca, área do cliente (/api/me) e webhook do Stripe
 *   admin   /api/admin/** (ROLE_ADMIN)
 *   crm     /api/crm/** (equipe interna, realm voe-familhas-crm; ADR 0013)
 *
 * Swagger UI em /swagger-ui.html e JSON em /v3/api-docs/{grupo}. Ligado só no perfil local
 * (application-local.properties); em outro ambiente, SPRINGDOC_ENABLED=true liga de propósito.
 *
 * Os contratos escritos à mão em docs/api continuam sendo a fonte dos tipos dos frontends; este documento
 * mostra o que o código expõe de fato e serve para explorar a API.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearer-jwt";

    @Bean
    OpenAPI familhasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Familhas API")
                        .version("v1")
                        .description("""
                                API da Loja, do Admin e do CRM interno da Voe Familhas. Tokens do Keycloak (realm \
                                familhas para a Loja, voe-familhas-crm para o CRM; audiência familhas-api). Erros em Problem Details (RFC 7807) com `code` estável nos erros de \
                                negócio. Toda resposta traz `X-Correlation-Id`; envie o mesmo header para seguir \
                                uma requisição nos logs."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token do Keycloak (realm familhas ou voe-familhas-crm). Veja docs/postman/README.md.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Loja (público e cliente)")
                .pathsToMatch("/api/**")
                .pathsToExclude("/api/admin/**", "/api/crm/**")
                .build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin (ROLE_ADMIN)")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    @Bean
    GroupedOpenApi crmApi() {
        return GroupedOpenApi.builder()
                .group("crm")
                .displayName("CRM interno (equipe Voe Familhas)")
                .pathsToMatch("/api/crm/**")
                .build();
    }
}
