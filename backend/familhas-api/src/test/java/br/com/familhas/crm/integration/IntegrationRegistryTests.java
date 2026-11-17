package br.com.familhas.crm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IntegrationRegistryTests {

    @Test
    void nothingIsConfiguredByDefaultAndRequireGives503Code() {
        IntegrationRegistry registry = new IntegrationRegistry(new IntegrationProperties("none", "none", "none", "", "", "", "", "", ""));
        assertThat(registry.statuses()).hasSize(CrmIntegration.values().length).noneMatch(IntegrationStatus::configured);
        assertThatThrownBy(() -> registry.require(CrmIntegration.AI_TEXT))
                .isInstanceOfSatisfying(IntegrationNotConfiguredException.class, e -> {
                    assertThat(e.code()).isEqualTo("INTEGRATION_NOT_CONFIGURED");
                    assertThat(e.getStatusCode().value()).isEqualTo(503);
                });
    }

    @Test
    void credentialsTurnIntegrationsOnWithoutExposingThem() {
        IntegrationRegistry registry = new IntegrationRegistry(
                new IntegrationProperties("claude", " NONE ", "none", "serp-secret", "meta-token", "", "", "", ""));
        assertThat(registry.isConfigured(CrmIntegration.AI_TEXT)).isTrue();
        assertThat(registry.isConfigured(CrmIntegration.AI_IMAGE)).isFalse();
        assertThat(registry.isConfigured(CrmIntegration.FLIGHT_SEARCH)).isTrue();
        assertThat(registry.isConfigured(CrmIntegration.INSTAGRAM)).as("falta o usuario do Instagram").isFalse();
        assertThat(registry.statuses().toString()).doesNotContain("serp-secret", "meta-token");
    }

    @Test
    void imageNeedsOpenAiAndItsKey() {
        assertThat(new IntegrationRegistry(new IntegrationProperties("none", "openai", "none", "", "", "", "", "", ""))
                .isConfigured(CrmIntegration.AI_IMAGE)).as("sem chave").isFalse();
        assertThat(new IntegrationRegistry(new IntegrationProperties("none", "dall-e", "none", "", "", "", "", "", "sk"))
                .isConfigured(CrmIntegration.AI_IMAGE)).as("provedor sem adapter").isFalse();
        IntegrationRegistry registry = new IntegrationRegistry(
                new IntegrationProperties("none", " OpenAI ", "none", "", "", "", "", "", "sk-segredo"));
        assertThat(registry.isConfigured(CrmIntegration.AI_IMAGE)).isTrue();
        assertThat(registry.isConfigured(CrmIntegration.AI_TEXT)).isFalse();
        assertThat(registry.statuses().toString()).doesNotContain("sk-segredo");
    }
}
