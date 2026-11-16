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

    public IntegrationRegistry(IntegrationProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured(CrmIntegration integration) {
        return switch (integration) {
            case AI_TEXT -> hasProvider(properties.aiTextProvider());
            case AI_IMAGE -> hasProvider(properties.aiImageProvider());
            case AI_VIDEO -> hasProvider(properties.aiVideoProvider());
            case FLIGHT_SEARCH -> !isBlank(properties.serpapiApiKey());
            case INSTAGRAM -> !isBlank(properties.metaAccessToken()) && !isBlank(properties.metaInstagramUserId());
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
            case AI_TEXT, AI_IMAGE, AI_VIDEO -> "Provedor ainda nao definido. Os campos ficam para preenchimento manual.";
            case FLIGHT_SEARCH -> "Defina SERPAPI_API_KEY no servidor.";
            case INSTAGRAM -> "Defina META_ACCESS_TOKEN e META_INSTAGRAM_USER_ID no servidor.";
        };
    }

    private static boolean hasProvider(String provider) {
        return !isBlank(provider) && !"none".equalsIgnoreCase(provider.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
