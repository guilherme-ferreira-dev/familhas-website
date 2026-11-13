package br.com.familhas.admin.payment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.admin.payment.export.PaymentExportFormat;
import br.com.familhas.admin.payment.export.PaymentExportWriter;
import br.com.familhas.common.BusinessTime;

import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;

/** Planilha de pagamentos (docs/api/admin.yaml). */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final AdminPaymentService paymentService;
    private final AuditService auditService;
    private final Clock clock;

    public AdminPaymentController(AdminPaymentService paymentService, AuditService auditService, Clock clock) {
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @GetMapping
    public PageResponse<AdminPaymentResponse> findAll(@Valid @ModelAttribute AdminPaymentFilter filter,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public AdminPaymentResponse findById(@PathVariable UUID id) {
        return paymentService.findById(id);
    }

    /**
     * Exporta a planilha filtrada em streaming (ADR 0011). A exportação é auditada antes de começar, com os
     * filtros usados: é uma saída de dados pessoais (LGPD).
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam String format,
            @Valid @ModelAttribute AdminPaymentFilter filter) {
        PaymentExportFormat exportFormat;
        try {
            exportFormat = PaymentExportFormat.from(format);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        // Valida o período antes de responder 200: depois que o streaming começa, não dá para mudar o status.
        AdminPaymentService.toSpecification(filter);
        auditService.record(AuditActor.current(), new AuditEntry(AuditAction.EXPORT, "payments", null, "GET",
                "/api/admin/payments/export", HttpStatus.OK.value(),
                ("format=" + exportFormat.extension() + " " + filter.describe()).trim()));

        String fileName = "pagamentos-" + LocalDateTime.now(clock.withZone(BusinessTime.ZONE)).format(FILE_STAMP)
                + "." + exportFormat.extension();
        StreamingResponseBody body = out -> {
            try (PaymentExportWriter writer = PaymentExportWriter.open(exportFormat, out)) {
                paymentService.forEachRow(filter, payment -> {
                    try {
                        writer.writeRow(payment);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        };
        return ResponseEntity.ok()
                .contentType(exportFormat.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
