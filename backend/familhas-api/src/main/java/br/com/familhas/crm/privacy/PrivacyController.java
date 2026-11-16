package br.com.familhas.crm.privacy;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.admin.audit.AuditedManually;
import jakarta.validation.Valid;

/** Privacidade (LGPD): {@code /api/crm/privacy} (ADR 0018). Só admin. */
@RestController
@RequestMapping("/api/crm/privacy")
public class PrivacyController {

    private final PrivacyService privacy;
    private final IncidentService incidents;

    public PrivacyController(PrivacyService privacy, IncidentService incidents) {
        this.privacy = privacy;
        this.incidents = incidents;
    }

    /** Relatório dos dados do titular (POST para o CPF não ir na URL nem no log de acesso). */
    @PostMapping("/subject-report")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:privacidade:exportar')")
    public SubjectReport report(@Valid @RequestBody SubjectRequest request) {
        return privacy.report(request);
    }

    @PostMapping("/anonymize")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:privacidade:excluir')")
    public AnonymizationReport anonymize(@Valid @RequestBody SubjectRequest request,
            @RequestParam(defaultValue = "false") boolean apply) {
        return privacy.anonymize(request, apply);
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('crm:privacidade:ler')")
    public List<IncidentResponse> incidents() {
        return incidents.list();
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:privacidade:criar')")
    public IncidentResponse createIncident(@Valid @RequestBody IncidentRequest request) {
        return incidents.create(request);
    }

    @PutMapping("/incidents/{id}")
    @PreAuthorize("hasAuthority('crm:privacidade:editar')")
    public IncidentResponse updateIncident(@PathVariable UUID id, @Valid @RequestBody IncidentRequest request) {
        return incidents.update(id, request);
    }
}
