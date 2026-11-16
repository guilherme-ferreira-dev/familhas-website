package br.com.familhas.crm;

import static br.com.familhas.crm.CrmAction.CREATE;
import static br.com.familhas.crm.CrmAction.DELETE;
import static br.com.familhas.crm.CrmAction.EXPORT;
import static br.com.familhas.crm.CrmAction.PUBLISH;
import static br.com.familhas.crm.CrmAction.READ;
import static br.com.familhas.crm.CrmAction.UPDATE;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Papéis da equipe no realm do CRM ({@code voe-familhas-crm}) e o que cada um pode fazer (ADR 0013).
 *
 * O papel do Keycloak ({@code crm-admin}...) vira a authority {@code ROLE_CRM_ADMIN}... e as permissões
 * {@code crm:<recurso>:<ação>}. Os papéis do realm da Loja nunca geram nada disto.
 */
public enum CrmRole {

    /** Tudo, inclusive equipe, restauração de backup e as telas da Loja ocultas no menu. */
    ADMIN("crm-admin"),
    /** Operação e marketing completos; lê equipe e auditoria; exporta backup, mas não restaura. */
    GESTOR("crm-gestor"),
    /** Atendimento: voos, pesquisas, vouchers, roteiros e documentos, sem excluir. */
    ATENDENTE("crm-atendente"),
    /** Conteúdo: posts, imagens e publicação no Instagram. */
    MARKETING("crm-marketing");

    private static final Set<CrmResource> OPERATIONS = EnumSet.of(CrmResource.DASHBOARD, CrmResource.CLIENT_FLIGHTS,
            CrmResource.FLIGHT_SEARCH, CrmResource.VOUCHERS, CrmResource.DOCUMENTS, CrmResource.ITINERARIES);
    private static final Set<CrmResource> CONTENT = EnumSet.of(CrmResource.QUOTE_POSTS, CrmResource.CRUISE_POSTS,
            CrmResource.SAVED_IMAGES, CrmResource.INSTAGRAM_POSTS);

    private final String keycloakRole;

    CrmRole(String keycloakRole) {
        this.keycloakRole = keycloakRole;
    }

    public String keycloakRole() {
        return keycloakRole;
    }

    public String authority() {
        return "ROLE_CRM_" + name();
    }

    public static Optional<CrmRole> fromKeycloakRole(String role) {
        return Arrays.stream(values()).filter(r -> r.keycloakRole.equals(role)).findFirst();
    }

    /** Permissões {@code crm:<recurso>:<ação>} do papel, em ordem estável. */
    public Set<String> permissions() {
        Set<String> result = new LinkedHashSet<>();
        switch (this) {
            case ADMIN -> {
                for (CrmResource resource : CrmResource.values()) {
                    for (CrmAction action : CrmAction.values()) {
                        result.add(CrmPermissions.authority(resource, action));
                    }
                }
            }
            case GESTOR -> {
                grant(result, OPERATIONS, READ, CREATE, UPDATE, DELETE, PUBLISH, EXPORT);
                grant(result, CONTENT, READ, CREATE, UPDATE, DELETE, PUBLISH, EXPORT);
                grant(result, EnumSet.of(CrmResource.TEAM, CrmResource.AUDIT), READ);
                grant(result, EnumSet.of(CrmResource.DATA_IMPORT), READ, CREATE);
                grant(result, EnumSet.of(CrmResource.BACKUP), READ, EXPORT);
            }
            case ATENDENTE -> {
                grant(result, EnumSet.of(CrmResource.DASHBOARD), READ);
                grant(result, EnumSet.of(CrmResource.CLIENT_FLIGHTS, CrmResource.VOUCHERS, CrmResource.ITINERARIES),
                        READ, CREATE, UPDATE);
                grant(result, EnumSet.of(CrmResource.FLIGHT_SEARCH, CrmResource.DOCUMENTS), READ, CREATE);
            }
            case MARKETING -> {
                grant(result, EnumSet.of(CrmResource.DASHBOARD, CrmResource.ITINERARIES, CrmResource.DOCUMENTS), READ);
                grant(result, CONTENT, READ, CREATE, UPDATE, DELETE, PUBLISH);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static void grant(Set<String> target, Set<CrmResource> resources, CrmAction... actions) {
        for (CrmResource resource : resources) {
            for (CrmAction action : actions) {
                target.add(CrmPermissions.authority(resource, action));
            }
        }
    }
}
