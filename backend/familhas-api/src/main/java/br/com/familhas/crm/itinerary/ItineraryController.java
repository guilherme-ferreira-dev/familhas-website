package br.com.familhas.crm.itinerary;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import br.com.familhas.common.PageResponse;
import br.com.familhas.crm.marketing.ItineraryAiRequest;
import br.com.familhas.crm.marketing.MarketingAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Gerar Roteiro: {@code /api/crm/itineraries} (ADR 0016). */
@RestController
@RequestMapping("/api/crm/itineraries")
public class ItineraryController {

    private final ItineraryService service;
    private final MarketingAiService ai;

    public ItineraryController(ItineraryService service, MarketingAiService ai) {
        this.service = service;
        this.ai = ai;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:roteiros:ler')")
    public PageResponse<ItinerarySummary> search(@RequestParam(required = false) @Size(max = 100) String q,
            Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:roteiros:ler')")
    public ItineraryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:roteiros:criar')")
    public ItineraryResponse create(@Valid @RequestBody ItineraryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:roteiros:editar')")
    public ItineraryResponse update(@PathVariable UUID id, @Valid @RequestBody ItineraryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('crm:roteiros:excluir')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Geração por IA a partir de cidade e dias, ou do PDF: consulta. Sem provedor: 503. */
    @PostMapping("/generate")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:roteiros:criar')")
    public void generate(@Valid @RequestBody ItineraryAiRequest request) {
        ai.generateItinerary(request);
    }
}
