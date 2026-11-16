package br.com.familhas.crm.flightsearch;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.admin.audit.AuditedManually;
import jakarta.validation.Valid;

/** Pesquisar Voos: {@code /api/crm/flight-search} (ADR 0015). */
@RestController
@RequestMapping("/api/crm/flight-search")
public class FlightSearchController {

    private final FlightSearchService service;
    private final FlightSearchGroupService groups;

    public FlightSearchController(FlightSearchService service, FlightSearchGroupService groups) {
        this.service = service;
        this.groups = groups;
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:ler')")
    public FlightSearchOptionsResponse options() {
        return service.options();
    }

    /** Pesquisa (consulta externa, não altera dados do CRM): não vira CREATE na auditoria. */
    @PostMapping
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:criar')")
    public FlightSearchResponse search(@Valid @RequestBody FlightSearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:ler')")
    public List<RecentSearchResponse> recent() {
        return service.recent();
    }

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:ler')")
    public List<FlightSearchGroupResponse> groups() {
        return groups.list();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:criar')")
    public FlightSearchGroupResponse createGroup(@Valid @RequestBody FlightSearchGroupRequest request) {
        return groups.create(request);
    }

    @PutMapping("/groups/{id}")
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:editar')")
    public FlightSearchGroupResponse updateGroup(@PathVariable UUID id,
            @Valid @RequestBody FlightSearchGroupRequest request) {
        return groups.update(id, request);
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('crm:pesquisa-voos:excluir')")
    public void deleteGroup(@PathVariable UUID id) {
        groups.delete(id);
    }
}
