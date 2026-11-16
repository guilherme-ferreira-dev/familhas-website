package br.com.familhas.crm.team;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.crm.CrmRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Equipe: {@code /api/crm/team} (ADR 0017). Ver: admin e gestor. Alterar: só admin. */
@RestController
@RequestMapping("/api/crm/team")
public class TeamController {

    private final TeamService service;

    public TeamController(TeamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:equipe:ler')")
    public List<TeamMember> members() {
        return service.members();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:equipe:criar')")
    public TeamMember invite(@Valid @RequestBody InviteRequest request) {
        return service.invite(request);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('crm:equipe:editar')")
    public TeamMember changeRole(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
        return service.changeRole(id, request.role());
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('crm:equipe:editar')")
    public TeamMember disable(@PathVariable String id) {
        return service.setEnabled(id, false);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('crm:equipe:editar')")
    public TeamMember enable(@PathVariable String id) {
        return service.setEnabled(id, true);
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAuthority('crm:equipe:editar')")
    public TeamMember resend(@PathVariable String id) {
        return service.resendAccess(id);
    }

    public record RoleRequest(@NotNull CrmRole role) {
    }
}
