package br.com.familhas.config;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * No perfil {@code prod}, confere a configuração de segurança antes de atender (ADR 0018). Qualquer problema derruba
 * a subida com a lista completa: segredo dos links de arquivo, issuers e CORS em https sem localhost, Swagger
 * desligado e a chave do backup no formato certo quando informada. Integrações opcionais (IA, SerpApi, Meta, e-mail,
 * equipe) não bloqueiam: aparecem como "não configuradas".
 */
@Component
@Profile("prod")
public class ProductionReadinessCheck {

    private final String linkSecret;
    private final String issuer;
    private final String crmIssuer;
    private final String cors;
    private final boolean springdoc;
    private final String backupKey;
    private final String metaPublicBaseUrl;

    public ProductionReadinessCheck(@Value("${familhas.storage.link-secret:}") String linkSecret,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuer,
            @Value("${familhas.security.crm.issuer-uri:}") String crmIssuer,
            @Value("${familhas.security.cors-allowed-origins:}") String cors,
            @Value("${springdoc.api-docs.enabled:false}") boolean springdoc,
            @Value("${familhas.backup.encryption-key:}") String backupKey,
            @Value("${familhas.integrations.meta-public-base-url:}") String metaPublicBaseUrl) {
        this.linkSecret = linkSecret;
        this.issuer = issuer;
        this.crmIssuer = crmIssuer;
        this.cors = cors;
        this.springdoc = springdoc;
        this.backupKey = backupKey;
        this.metaPublicBaseUrl = metaPublicBaseUrl;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void verify() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuracao insegura para producao:\n - " + String.join("\n - ", problems));
        }
    }

    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (linkSecret == null || linkSecret.length() < 32) {
            problems.add("STORAGE_LINK_SECRET precisa de 32 ou mais caracteres");
        }
        requireHttps(problems, "KEYCLOAK_ISSUER_URI", issuer);
        requireHttps(problems, "KEYCLOAK_CRM_ISSUER_URI", crmIssuer);
        if (cors == null || cors.isBlank()) {
            problems.add("CORS_ALLOWED_ORIGINS precisa listar os enderecos da Loja e do CRM");
        } else {
            for (String origin : cors.split(",")) {
                requireHttps(problems, "CORS_ALLOWED_ORIGINS (" + origin.strip() + ")", origin.strip());
            }
        }
        if (springdoc) {
            problems.add("SPRINGDOC_ENABLED precisa ficar desligado em producao");
        }
        if (backupKey != null && !backupKey.isBlank()) {
            try {
                if (Base64.getDecoder().decode(backupKey.strip()).length != 32) {
                    problems.add("BACKUP_ENCRYPTION_KEY precisa de 32 bytes em Base64");
                }
            } catch (IllegalArgumentException e) {
                problems.add("BACKUP_ENCRYPTION_KEY nao e Base64 valido");
            }
        }
        if (metaPublicBaseUrl != null && !metaPublicBaseUrl.isBlank()) {
            requireHttps(problems, "META_PUBLIC_BASE_URL", metaPublicBaseUrl);
        }
        return problems;
    }

    private static void requireHttps(List<String> problems, String name, String url) {
        String value = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (!value.startsWith("https://") || value.contains("://localhost") || value.contains("://127.")) {
            problems.add(name + " precisa ser https e nao pode ser localhost");
        }
    }
}
