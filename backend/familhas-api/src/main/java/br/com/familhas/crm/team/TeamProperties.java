package br.com.familhas.crm.team;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Equipe (ADR 0017): conta de serviço do realm do CRM na Admin API do Keycloak. Só os papéis de gestão de usuários
 * do próprio realm (view-users, query-users, manage-users). O segredo fica em {@code familhas.integrations.keycloak-team-secret}; sem ele, a tela
 * de equipe fica "não configurada".
 *
 * @param keycloakUrl    base do Keycloak
 * @param realm          realm do CRM
 * @param clientId       client confidencial com service account
 * @param crmUrl         para onde o e-mail de convite leva depois de definir a senha
 * @param inviteLifespan validade do link do convite
 */
@ConfigurationProperties("familhas.team")
public record TeamProperties(
        @DefaultValue("https://localhost:8443") String keycloakUrl,
        @DefaultValue("voe-familhas-crm") String realm,
        @DefaultValue("familhas-crm-team") String clientId,
        @DefaultValue("http://localhost:4300/") String crmUrl,
        @DefaultValue("PT72H") Duration inviteLifespan) {
}
