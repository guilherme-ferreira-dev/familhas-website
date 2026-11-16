package br.com.familhas.crm.team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.crm.CrmRole;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationRegistry;

/**
 * Equipe (JSON: "Gestão de membros e convites da equipe (somente administrador)"; ADR 0017). O CRM só mexe nos papéis
 * do CRM e só em quem já é da equipe: um id de outro usuário do realm (ex.: a conta de serviço) dá 404.
 *
 * Travas: ninguém desativa a si mesmo nem troca o próprio papel; o último admin ativo não perde o papel nem é
 * desativado. Desativar encerra as sessões abertas. Convite = usuário novo com senha a definir pelo link do
 * Keycloak (e TOTP obrigatório para admin, pelo fluxo do realm).
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);
    static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";
    static final String CONFIGURE_TOTP = "CONFIGURE_TOTP";

    private final TeamDirectory directory;
    private final IntegrationRegistry integrations;

    public TeamService(TeamDirectory directory, IntegrationRegistry integrations) {
        this.directory = directory;
        this.integrations = integrations;
    }

    public List<TeamMember> members() {
        integrations.require(CrmIntegration.TEAM_DIRECTORY);
        String me = AuditActor.current().subject();
        List<TeamMember> members = new ArrayList<>();
        for (TeamDirectory.User user : directory.users()) {
            // Quem não tem papel do CRM não é da equipe; com dois papéis, vale o mais alto (ADMIN primeiro).
            crmRole(directory.realmRoles(user.id())).ifPresent(role -> members.add(member(user, role, me)));
        }
        return members.stream()
                .sorted(Comparator.comparing((TeamMember m) -> !m.enabled())
                        .thenComparing(m -> m.role().ordinal())
                        .thenComparing(m -> (m.firstName() == null ? "" : m.firstName()).toLowerCase(Locale.ROOT)))
                .toList();
    }

    public TeamMember invite(InviteRequest request) {
        integrations.require(CrmIntegration.TEAM_DIRECTORY);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        List<String> actions = new ArrayList<>(List.of(UPDATE_PASSWORD));
        if (request.role() == CrmRole.ADMIN) {
            actions.add(CONFIGURE_TOTP);
        }
        String id;
        try {
            id = directory.create(new TeamDirectory.User(null, email, email, request.firstName().strip(),
                    request.lastName().strip(), true, null, actions));
        } catch (KeycloakAdminException e) {
            if (e.status() == 409) {
                throw new BusinessException(HttpStatus.CONFLICT, "MEMBER_EXISTS",
                        "Ja existe um usuario com esse e-mail.");
            }
            throw unavailable(e);
        }
        directory.addRealmRoles(id, Set.of(request.role().keycloakRole()));
        try {
            directory.sendActionsEmail(id, actions);
        } catch (KeycloakAdminException e) {
            // O usuário existe e tem papel; o e-mail pode ser reenviado pela tela.
            log.warn("Convite criado, mas o e-mail nao saiu: user={} status={}", id, e.status());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "INVITE_EMAIL_FAILED",
                    "Membro criado, mas o e-mail do convite nao saiu. Use \"Reenviar acesso\".");
        }
        return find(id).orElseThrow();
    }

    public TeamMember changeRole(String id, CrmRole role) {
        integrations.require(CrmIntegration.TEAM_DIRECTORY);
        TeamMember member = require(id);
        requireNotSelf(member, "Voce nao pode trocar o proprio papel.");
        if (member.role() == role) {
            return member;
        }
        if (member.role() == CrmRole.ADMIN && member.enabled() && activeAdmins() <= 1) {
            throw lastAdmin();
        }
        Set<String> crmRoles = EnumSet.allOf(CrmRole.class).stream().map(CrmRole::keycloakRole)
                .collect(Collectors.toSet());
        Set<String> current = directory.realmRoles(id).stream().filter(crmRoles::contains).collect(Collectors.toSet());
        directory.addRealmRoles(id, Set.of(role.keycloakRole()));
        current.remove(role.keycloakRole());
        directory.removeRealmRoles(id, current);
        if (role == CrmRole.ADMIN && !directory.hasOtp(id) && !member.pendingActions().contains(CONFIGURE_TOTP)) {
            List<String> actions = new ArrayList<>(member.pendingActions());
            actions.add(CONFIGURE_TOTP);
            directory.setRequiredActions(id, actions);
        }
        // Papel novo vale no próximo token; encerrar as sessões evita o papel antigo nos próximos minutos.
        directory.logout(id);
        return find(id).orElseThrow();
    }

    public TeamMember setEnabled(String id, boolean enabled) {
        integrations.require(CrmIntegration.TEAM_DIRECTORY);
        TeamMember member = require(id);
        if (!enabled) {
            requireNotSelf(member, "Voce nao pode desativar a propria conta.");
            if (member.role() == CrmRole.ADMIN && member.enabled() && activeAdmins() <= 1) {
                throw lastAdmin();
            }
        }
        directory.setEnabled(id, enabled);
        if (!enabled) {
            directory.logout(id);
        }
        return find(id).orElseThrow();
    }

    /** Reenvia o link de acesso (definir senha; e TOTP, se pendente). */
    public TeamMember resendAccess(String id) {
        integrations.require(CrmIntegration.TEAM_DIRECTORY);
        TeamMember member = require(id);
        if (!member.enabled()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "MEMBER_DISABLED",
                    "Reative o membro antes de reenviar o acesso.");
        }
        List<String> actions = new ArrayList<>(List.of(UPDATE_PASSWORD));
        if (member.pendingActions().contains(CONFIGURE_TOTP)) {
            actions.add(CONFIGURE_TOTP);
        }
        try {
            directory.sendActionsEmail(id, actions);
        } catch (KeycloakAdminException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "INVITE_EMAIL_FAILED",
                    "O e-mail nao saiu. Confira o SMTP do Keycloak.");
        }
        return member;
    }

    private long activeAdmins() {
        return directory.users().stream().filter(TeamDirectory.User::enabled)
                .filter(u -> directory.realmRoles(u.id()).contains(CrmRole.ADMIN.keycloakRole())).count();
    }

    private static Optional<CrmRole> crmRole(Set<String> roles) {
        return EnumSet.allOf(CrmRole.class).stream().filter(r -> roles.contains(r.keycloakRole())).findFirst();
    }

    private TeamMember require(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
            throw notFound();
        }
        return find(id).orElseThrow(TeamService::notFound);
    }

    /** O usuário só conta como membro se tiver algum papel do CRM. */
    private Optional<TeamMember> find(String id) {
        TeamDirectory.User user;
        try {
            user = directory.user(id);
        } catch (KeycloakAdminException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw unavailable(e);
        }
        return crmRole(directory.realmRoles(id)).map(role -> member(user, role, AuditActor.current().subject()));
    }

    private TeamMember member(TeamDirectory.User user, CrmRole role, String me) {
        return new TeamMember(user.id(), user.username(), user.email(), user.firstName(), user.lastName(), role,
                user.enabled(), directory.hasOtp(user.id()), user.requiredActions(),
                user.createdTimestamp() == null ? null : Instant.ofEpochMilli(user.createdTimestamp()),
                user.id().equals(me));
    }

    private static void requireNotSelf(TeamMember member, String message) {
        if (member.self()) {
            throw new BusinessException(HttpStatus.CONFLICT, "SELF_CHANGE_NOT_ALLOWED", message);
        }
    }

    private static BusinessException lastAdmin() {
        return new BusinessException(HttpStatus.CONFLICT, "LAST_ADMIN",
                "Este e o ultimo administrador ativo. Promova outra pessoa antes.");
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "Membro nao encontrado na equipe.");
    }

    private static BusinessException unavailable(KeycloakAdminException e) {
        log.warn("Admin API do Keycloak falhou: status={}", e.status());
        return new BusinessException(HttpStatus.BAD_GATEWAY, "TEAM_DIRECTORY_FAILED",
                "O Keycloak nao respondeu. Tente de novo em instantes.");
    }
}
