package br.com.familhas.admin.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;

/** Consulta do log de auditoria. Sempre do mais recente para o mais antigo. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> findAll(@Valid @ModelAttribute AuditLogFilter filter,
            @PageableDefault(size = 50) Pageable pageable) {
        return auditService.findAll(filter, pageable);
    }
}
