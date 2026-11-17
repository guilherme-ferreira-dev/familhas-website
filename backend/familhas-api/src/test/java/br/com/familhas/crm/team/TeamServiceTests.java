package br.com.familhas.crm.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import br.com.familhas.api.BusinessException;
import br.com.familhas.crm.CrmRole;
import br.com.familhas.crm.integration.IntegrationNotConfiguredException;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.integration.IntegrationRegistry;

class TeamServiceTests {

    static final String ME = "11111111-1111-1111-1111-111111111111";
    static final String ANA = "22222222-2222-2222-2222-222222222222";
    static final String BRUNO = "33333333-3333-3333-3333-333333333333";

    private final TeamDirectory directory = mock(TeamDirectory.class);
    private final TeamService service = new TeamService(directory,
            new IntegrationRegistry(new IntegrationProperties("none", "none", "none", "", "", "", "", "segredo", "")));

    @BeforeEach
    void loggedInAsAdmin() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(ME).claim("preferred_username", "admin.crm")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        TeamDirectory.User me = user(ME, "Eu", true, "crm-admin");
        TeamDirectory.User ana = user(ANA, "Ana", true, "crm-atendente");
        TeamDirectory.User bruno = user(BRUNO, "Bruno", false, "crm-gestor");
        TeamDirectory.User service = user("77777777-7777-7777-7777-777777777777", "Servico", true);
        when(directory.users()).thenReturn(List.of(bruno, ana, service, me));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsActiveMembersFirstWithRoleAndSelfFlag() {
        List<TeamMember> members = service.members();
        assertThat(members).extracting(TeamMember::firstName).containsExactly("Eu", "Ana", "Bruno");
        assertThat(members.get(0).self()).isTrue();
        assertThat(members.get(1).role()).isEqualTo(CrmRole.ATENDENTE);
        assertThat(members.get(2).enabled()).isFalse();
    }

    @Test
    void inviteCreatesUserWithRoleAndSendsTheKeycloakEmail() {
        when(directory.create(any())).thenReturn("44444444-4444-4444-4444-444444444444");
        user("44444444-4444-4444-4444-444444444444", "Carla", true, "crm-admin");

        service.invite(new InviteRequest(" Carla@Familhas.com ", "Carla", "Lima", CrmRole.ADMIN));

        ArgumentCaptor<TeamDirectory.User> created = ArgumentCaptor.forClass(TeamDirectory.User.class);
        verify(directory).create(created.capture());
        assertThat(created.getValue().username()).isEqualTo("carla@familhas.com");
        assertThat(created.getValue().requiredActions()).containsExactly("UPDATE_PASSWORD", "CONFIGURE_TOTP");
        verify(directory).addRealmRoles("44444444-4444-4444-4444-444444444444", Set.of("crm-admin"));
        verify(directory).sendActionsEmail("44444444-4444-4444-4444-444444444444", List.of("UPDATE_PASSWORD", "CONFIGURE_TOTP"));
    }

    @Test
    void duplicatedEmailIs409() {
        when(directory.create(any())).thenThrow(new KeycloakAdminException(409, "exists"));
        assertCode(() -> service.invite(new InviteRequest("ana@x.com", "Ana", "S", CrmRole.MARKETING)), "MEMBER_EXISTS");
    }

    @Test
    void selfAndLastAdminAreProtected() {
        assertCode(() -> service.setEnabled(ME, false), "SELF_CHANGE_NOT_ALLOWED");
        assertCode(() -> service.changeRole(ME, CrmRole.GESTOR), "SELF_CHANGE_NOT_ALLOWED");
        verify(directory, never()).setEnabled(anyString(), eq(false));
    }

    @Test
    void lastActiveAdminCannotLoseTheRole() {
        Jwt other = Jwt.withTokenValue("t").header("alg", "none").subject(ANA).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(other));
        assertCode(() -> service.changeRole(ME, CrmRole.MARKETING), "LAST_ADMIN");
        assertCode(() -> service.setEnabled(ME, false), "LAST_ADMIN");
    }

    @Test
    void changingRoleSwapsOnlyCrmRolesRequiresTotpForAdminAndEndsSessions() {
        when(directory.realmRoles(ANA)).thenReturn(Set.of("crm-atendente", "default-roles-voe-familhas-crm", "crm-equipe"));
        service.changeRole(ANA, CrmRole.ADMIN);
        verify(directory).addRealmRoles(ANA, Set.of("crm-admin"));
        verify(directory).removeRealmRoles(ANA, Set.of("crm-atendente"));
        verify(directory).setRequiredActions(ANA, List.of("CONFIGURE_TOTP"));
        verify(directory).logout(ANA);
    }

    @Test
    void usersOutsideTheTeamAndBadIdsAre404() {
        String outsider = "55555555-5555-5555-5555-555555555555";
        user(outsider, "Servico", true);
        assertCode(() -> service.setEnabled(outsider, false), "MEMBER_NOT_FOUND");
        assertCode(() -> service.setEnabled("../admin", false), "MEMBER_NOT_FOUND");
        when(directory.user("66666666-6666-6666-6666-666666666666")).thenThrow(new KeycloakAdminException(404, "x"));
        assertCode(() -> service.resendAccess("66666666-6666-6666-6666-666666666666"), "MEMBER_NOT_FOUND");
    }

    @Test
    void disablingEndsSessionsAndDisabledMemberCannotGetAccessEmail() {
        service.setEnabled(ANA, false);
        verify(directory).setEnabled(ANA, false);
        verify(directory).logout(ANA);
        assertCode(() -> service.resendAccess(BRUNO), "MEMBER_DISABLED");
    }

    @Test
    void withoutSecretTheTeamIsNotConfigured() {
        TeamService off = new TeamService(directory,
                new IntegrationRegistry(new IntegrationProperties("none", "none", "none", "", "", "", "", "", "")));
        assertThatThrownBy(off::members).isInstanceOf(IntegrationNotConfiguredException.class);
    }

    private TeamDirectory.User user(String id, String name, boolean enabled, String... roles) {
        TeamDirectory.User user = new TeamDirectory.User(id, name.toLowerCase(), name.toLowerCase() + "@x.com", name,
                "Teste", enabled, 1_700_000_000_000L, List.of());
        when(directory.user(id)).thenReturn(user);
        when(directory.realmRoles(id)).thenReturn(Set.of(roles));
        return user;
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
