package br.com.familhas.crm.clientflight;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Voos Clientes: {@code /api/crm/client-flights} (ADR 0015). */
@RestController
@RequestMapping("/api/crm/client-flights")
public class ClientFlightController {

    private final ClientFlightService service;

    public ClientFlightController(ClientFlightService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:voos-clientes:ler')")
    public PageResponse<ClientFlightSummary> search(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 60) String airline, Pageable pageable) {
        return service.search(new ClientFlightFilter(q, archived, from, to, airline), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:voos-clientes:ler')")
    public ClientFlightResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:voos-clientes:criar')")
    public ClientFlightResponse create(@Valid @RequestBody ClientFlightRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:voos-clientes:editar')")
    public ClientFlightResponse update(@PathVariable UUID id, @Valid @RequestBody ClientFlightRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('crm:voos-clientes:editar')")
    public ClientFlightResponse archive(@PathVariable UUID id) {
        return service.archive(id, true);
    }

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasAuthority('crm:voos-clientes:editar')")
    public ClientFlightResponse unarchive(@PathVariable UUID id) {
        return service.archive(id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('crm:voos-clientes:excluir')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('crm:voos-clientes:editar')")
    public ClientFlightResponse attach(@PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.attach(id, file);
    }

    @DeleteMapping("/{id}/attachments/{fileId}")
    @PreAuthorize("hasAuthority('crm:voos-clientes:excluir')")
    public ClientFlightResponse detach(@PathVariable UUID id, @PathVariable UUID fileId) {
        return service.detach(id, fileId);
    }
}
