package br.com.familhas.crm.voucher;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Emitir Voucher: {@code /api/crm/vouchers} (ADR 0015). */
@RestController
@RequestMapping("/api/crm/vouchers")
public class VoucherController {

    private final VoucherService service;

    public VoucherController(VoucherService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:vouchers:ler')")
    public PageResponse<VoucherSummary> search(@RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) VoucherType type, @RequestParam(required = false) VoucherStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Pageable pageable) {
        return service.search(q, type, status, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:vouchers:ler')")
    public VoucherResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('crm:vouchers:criar')")
    public VoucherResponse issue(@Valid @RequestBody VoucherRequest request) {
        return service.issue(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('crm:vouchers:editar')")
    public VoucherResponse update(@PathVariable UUID id, @Valid @RequestBody VoucherRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('crm:vouchers:editar')")
    public VoucherResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    /** Documento A4 com o CPF inteiro: auditado como DOWNLOAD no service. */
    @GetMapping("/{id}/document")
    @PreAuthorize("hasAuthority('crm:vouchers:ler')")
    public VoucherDocument document(@PathVariable UUID id) {
        return service.document(id);
    }

    /** Extração por IA: consulta, não grava. Sem provedor configurado: 503. */
    @PostMapping("/extract")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:vouchers:criar')")
    public VoucherDetails extract(@Valid @RequestBody VoucherExtractRequest request) {
        return service.extract(request.type(), request.fileId());
    }
}
