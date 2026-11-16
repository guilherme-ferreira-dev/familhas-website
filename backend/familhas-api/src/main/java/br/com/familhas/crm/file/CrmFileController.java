package br.com.familhas.crm.file;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.admin.audit.AuditedManually;
import br.com.familhas.common.PageResponse;

/**
 * Arquivos privados do CRM. A permissão depende da categoria do arquivo, então é conferida no service
 * ({@code CrmAccess}); a rota em si já exige um papel do CRM. Só {@code GET .../content} é público, e só com link
 * assinado e dentro do prazo (ADR 0014).
 */
@RestController
@RequestMapping("/api/crm/files")
public class CrmFileController {

    private final CrmFileService service;

    public CrmFileController(CrmFileService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CrmFileResponse upload(@RequestParam FileCategory category, @RequestPart("file") MultipartFile file) {
        return service.upload(category, file);
    }

    @GetMapping
    public PageResponse<CrmFileResponse> search(@RequestParam FileCategory category,
            @RequestParam(required = false) String name, Pageable pageable) {
        return service.search(category, name, pageable);
    }

    @GetMapping("/{id}")
    public CrmFileResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/link")
    @AuditedManually
    public FileLinkResponse link(@PathVariable UUID id) {
        return service.createLink(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id, @RequestParam long expires,
            @RequestParam String signature) {
        CrmFileService.StoredContent content = service.openSigned(id, expires, signature);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.name(), StandardCharsets.UTF_8).build().toString())
                // Conteúdo enviado por usuário: nunca roda script nem é embutido em outra origem.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(content.stream()));
    }
}
