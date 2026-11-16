package br.com.familhas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProductionReadinessCheckTests {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void secureConfigurationPasses() {
        ProductionReadinessCheck check = new ProductionReadinessCheck("x".repeat(40),
                "https://sso.familhas.tur.br/realms/familhas", "https://sso.familhas.tur.br/realms/voe-familhas-crm",
                "https://familhas.tur.br, https://crm.familhas.tur.br", false, KEY, "https://api.familhas.tur.br");
        assertThat(check.problems()).isEmpty();
        check.verify();
    }

    @Test
    void localDefaultsAreAllReportedAtOnce() {
        ProductionReadinessCheck check = new ProductionReadinessCheck("", "https://localhost:8443/realms/familhas",
                "http://sso.exemplo/realms/voe-familhas-crm", "http://localhost:4300", true, "curta", "http://api");
        assertThat(check.problems()).hasSize(7)
                .anyMatch(p -> p.startsWith("STORAGE_LINK_SECRET"))
                .anyMatch(p -> p.startsWith("KEYCLOAK_ISSUER_URI"))
                .anyMatch(p -> p.startsWith("KEYCLOAK_CRM_ISSUER_URI"))
                .anyMatch(p -> p.startsWith("CORS_ALLOWED_ORIGINS"))
                .anyMatch(p -> p.startsWith("SPRINGDOC_ENABLED"))
                .anyMatch(p -> p.startsWith("BACKUP_ENCRYPTION_KEY"))
                .anyMatch(p -> p.startsWith("META_PUBLIC_BASE_URL"));
        assertThatThrownBy(check::verify).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuracao insegura para producao");
    }

    /** Com os arquivos de propriedades reais: os padrões locais não sobem em prod; a configuração segura sobe. */
    @Test
    void prodProfileReadsTheRealPropertiesAndRefusesLocalDefaults() {
        assertThatThrownBy(() -> start()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_LINK_SECRET").hasMessageContaining("KEYCLOAK_ISSUER_URI")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        try (ConfigurableApplicationContext context = start(
                "--familhas.storage.link-secret=" + "s".repeat(40),
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://sso.familhas.tur.br/realms/familhas",
                "--familhas.security.crm.issuer-uri=https://sso.familhas.tur.br/realms/voe-familhas-crm",
                "--familhas.security.cors-allowed-origins=https://crm.familhas.tur.br")) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    /** Argumentos de linha de comando: vencem o application.properties, como as variáveis em produção. */
    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(ProductionReadinessCheck.class).web(WebApplicationType.NONE)
                .profiles("prod").run(args);
    }
}
