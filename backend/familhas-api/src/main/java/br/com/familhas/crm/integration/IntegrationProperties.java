package br.com.familhas.crm.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credenciais das integrações, só por variável de ambiente (nunca no Git nem no navegador). Vazio = não configurado.
 * Os provedores de IA ainda não foram escolhidos: {@code ai.*-provider} fica em "none" até lá (ADR 0014).
 * {@code metaPublicBaseUrl}: endereço público (https) da API, de onde a Meta baixa as imagens e vídeos por link
 * assinado na hora de publicar (ADR 0016). Sem ele, publicar no Instagram fica "não configurado".
 */
@ConfigurationProperties("familhas.integrations")
public record IntegrationProperties(
        @DefaultValue("none") String aiTextProvider,
        @DefaultValue("none") String aiImageProvider,
        @DefaultValue("none") String aiVideoProvider,
        @DefaultValue("") String serpapiApiKey,
        @DefaultValue("") String metaAccessToken,
        @DefaultValue("") String metaInstagramUserId,
        @DefaultValue("") String metaPublicBaseUrl,
        @DefaultValue("") String keycloakTeamSecret) {
}
