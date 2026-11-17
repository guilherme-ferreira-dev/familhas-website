package br.com.familhas.crm.integration;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Diz o que está configurado. Os módulos chamam {@link #require} antes de usar uma integração: sem credencial,
 * a API responde 503 INTEGRATION_NOT_CONFIGURED e a tela oferece o caminho manual, sem fingir que funcionou.
 */
@Component
public class IntegrationRegistry {

    private final IntegrationProperties properties;
    private final String mailHost;

    public IntegrationRegistry(IntegrationProperties properties) {
        this(properties, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IntegrationRegistry(IntegrationProperties properties,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}") String mailHost) {
        this.properties = properties;
        this.mailHost = mailHost;
    }

    public boolean isConfigured(CrmIntegration integration) {
        return switch (integration) {
            case AI_TEXT -> hasProvider(properties.aiTextProvider());
            case AI_IMAGE -> isOpenAi(properties.aiImageProvider()) && !isBlank(properties.openaiApiKey());
            case AI_VIDEO -> hasProvider(properties.aiVideoProvider());
            case FLIGHT_SEARCH -> !isBlank(properties.serpapiApiKey());
            case INSTAGRAM -> !isBlank(properties.metaAccessToken()) && !isBlank(properties.metaInstagramUserId())
                    && !isBlank(properties.metaPublicBaseUrl());
            case TEAM_DIRECTORY -> !isBlank(properties.keycloakTeamSecret());
            case EMAIL -> !isBlank(mailHost);
        };
    }

    public void require(CrmIntegration integration) {
        if (!isConfigured(integration)) {
            throw new IntegrationNotConfiguredException(integration);
        }
    }

    public List<IntegrationStatus> statuses() {
        return Arrays.stream(CrmIntegration.values())
                .map(i -> new IntegrationStatus(i, i.label(), isConfigured(i), detail(i)))
                .toList();
    }

    private String detail(CrmIntegration integration) {
        if (isConfigured(integration)) {
            return "Configurada";
        }
        return switch (integration) {
            case AI_TEXT, AI_VIDEO -> "Provedor ainda não definido. Os campos ficam para preenchimento manual.";
            case AI_IMAGE -> isOpenAi(properties.aiImageProvider()) ? "Defina OPENAI_API_KEY no servidor."
                    : "Defina AI_IMAGE_PROVIDER=openai e OPENAI_API_KEY no servidor. Enquanto isso, envie uma foto.";
            case FLIGHT_SEARCH -> "Defina SERPAPI_API_KEY no servidor.";
            case INSTAGRAM -> "Defina META_ACCESS_TOKEN, META_INSTAGRAM_USER_ID e META_PUBLIC_BASE_URL no servidor.";
            case TEAM_DIRECTORY -> "Defina KEYCLOAK_CRM_TEAM_SECRET (conta de serviço do realm do CRM) no servidor.";
            case EMAIL -> "Defina MAIL_HOST (servidor SMTP) no servidor.";
        };
    }

    /** Imagem só tem adapter da OpenAI (gpt-image-1, ADR 0019): outro nome continua "não configurado". */
    static final String OPENAI = "openai";

    private static boolean isOpenAi(String provider) {
        return provider != null && OPENAI.equalsIgnoreCase(provider.trim());
    }

    private static boolean hasProvider(String provider) {
        return !isBlank(provider) && !"none".equalsIgnoreCase(provider.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
