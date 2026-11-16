package br.com.familhas.crm.backup;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.admin.audit.AuditedManually;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Backup e restauração: {@code /api/crm/backups} (ADR 0017). Auditoria feita no service (EXPORT, RESTORE). */
@RestController
@RequestMapping("/api/crm/backups")
public class BackupController {

    private final BackupService service;

    public BackupController(BackupService service) {
        this.service = service;
    }

    /** Download direto do backup completo (não guarda nada no servidor). */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('crm:backup:exportar')")
    public ResponseEntity<byte[]> export() {
        return file(service.export("download direto"), "backup-crm-voe-familhas-" + LocalDate.now() + ".json");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:backup:ler')")
    public List<StoredBackup> stored() {
        return service.listStored();
    }

    /** Guarda o backup criptografado e manda por e-mail o link para baixar no CRM. */
    @PostMapping("/email")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:backup:exportar')")
    public StoredBackup email(@Valid @RequestBody EmailRequest request) {
        return service.emailBackup(request.to());
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('crm:backup:exportar')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        return file(service.downloadStored(id), "backup-crm-voe-familhas-" + id + ".json");
    }

    /** Prévia ({@code apply=false}, padrão) ou restauração do arquivo enviado. */
    @PostMapping(path = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:backup:restaurar')")
    public RestoreReport restore(@RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean apply) throws IOException {
        return service.restore(file.getBytes(), apply);
    }

    private static ResponseEntity<byte[]> file(byte[] content, String name) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(content);
    }

    public record EmailRequest(@Email @Size(max = 180) String to) {
    }
}
