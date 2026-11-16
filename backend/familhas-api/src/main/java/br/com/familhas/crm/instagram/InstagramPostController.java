package br.com.familhas.crm.instagram;

import java.util.Set;
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
import br.com.familhas.crm.marketing.MarketingAiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Criar posts Insta: {@code /api/crm/instagram-posts} (ADR 0016). */
@RestController
@RequestMapping("/api/crm/instagram-posts")
public class InstagramPostController {

    private final InstagramPostService service;
    private final InstagramPublisher publisher;
    private final MarketingAiService ai;

    public InstagramPostController(InstagramPostService service, InstagramPublisher publisher, MarketingAiService ai) {
        this.service = service;
        this.publisher = publisher;
        this.ai = ai;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:posts-insta:ler')")
    public PageResponse<InstagramPostSummary> list(@RequestParam(required = false) Set<PostStatus> status,
            Pageable pageable) {
        return service.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:posts-insta:ler')")
    public InstagramPostResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:posts-insta:criar')")
    public InstagramPostResponse create(@Valid @RequestBody InstagramPostRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:posts-insta:editar')")
    public InstagramPostResponse update(@PathVariable UUID id, @Valid @RequestBody InstagramPostRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAuthority('crm:posts-insta:editar')")
    public InstagramPostResponse ready(@PathVariable UUID id) {
        return service.markReady(id);
    }

    /** Publica (assíncrono): auditado como PUBLISH pelo publicador, com o resultado. */
    @PostMapping("/{id}/publish")
    @AuditedManually
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('crm:posts-insta:publicar')")
    public InstagramPostResponse publish(@PathVariable UUID id) {
        return publisher.publish(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('crm:posts-insta:publicar')")
    public InstagramPostResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
        return service.resolveUncertain(id, request.published(), request.permalink());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('crm:posts-insta:excluir')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    /** Geração por IA: consulta (não grava). Sem provedor: 503. */
    @PostMapping("/generate")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:posts-insta:criar')")
    public InstagramPostRequest generate(@Valid @RequestBody InstagramGenerateRequest request) {
        return ai.generateInstagramPost(request);
    }

    public record ResolveRequest(boolean published, @Size(max = 300) String permalink) {
    }
}
