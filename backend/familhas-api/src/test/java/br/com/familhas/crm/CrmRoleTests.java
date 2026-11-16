package br.com.familhas.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CrmRoleTests {

    @Test
    void adminHasEveryResourceAndAction() {
        assertThat(CrmRole.ADMIN.permissions())
                .hasSize(CrmResource.values().length * CrmAction.values().length)
                .contains("crm:equipe:criar", "crm:backup:restaurar", "crm:posts-insta:publicar");
    }

    @ParameterizedTest
    @EnumSource(CrmRole.class)
    void everyRoleIsASubsetOfAdminAndCanSeeTheDashboard(CrmRole role) {
        assertThat(CrmRole.ADMIN.permissions()).containsAll(role.permissions());
        assertThat(role.permissions()).contains("crm:dashboard:ler");
        assertThat(role.permissions()).allMatch(p -> p.matches("crm:[a-z-]+:[a-z]+"));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "GESTOR,    crm:backup:exportar,         true",
            "GESTOR,    crm:backup:restaurar,        false",
            "GESTOR,    crm:equipe:ler,              true",
            "GESTOR,    crm:equipe:criar,            false",
            "GESTOR,    crm:vouchers:excluir,        true",
            "ATENDENTE, crm:vouchers:criar,          true",
            "ATENDENTE, crm:voos-clientes:editar,    true",
            "ATENDENTE, crm:voos-clientes:excluir,   false",
            "ATENDENTE, crm:posts-insta:ler,         false",
            "ATENDENTE, crm:backup:exportar,         false",
            "ATENDENTE, crm:equipe:ler,              false",
            "MARKETING, crm:posts-insta:publicar,    true",
            "MARKETING, crm:posts-cotacao:excluir,   true",
            "MARKETING, crm:vouchers:ler,            false",
            "MARKETING, crm:voos-clientes:ler,       false",
            "MARKETING, crm:roteiros:editar,         false" })
    void permissionMatrix(CrmRole role, String permission, boolean granted) {
        assertThat(role.permissions().contains(permission)).isEqualTo(granted);
    }

    @Test
    void onlyKnownKeycloakRolesMap() {
        assertThat(CrmRole.fromKeycloakRole("crm-atendente")).contains(CrmRole.ATENDENTE);
        assertThat(CrmRole.fromKeycloakRole("ROLE_ADMIN")).isEmpty();
        assertThat(CrmRole.fromKeycloakRole("CRM-ADMIN")).isEmpty();
        assertThat(Arrays.stream(CrmRole.values()).map(CrmRole::authority))
                .containsExactly("ROLE_CRM_ADMIN", "ROLE_CRM_GESTOR", "ROLE_CRM_ATENDENTE", "ROLE_CRM_MARKETING");
    }

    @Test
    void permissionsAreImmutable() {
        Set<String> permissions = CrmRole.MARKETING.permissions();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> permissions.add("crm:equipe:criar"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
