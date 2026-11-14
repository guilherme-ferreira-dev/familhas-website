package br.com.familhas.admin.quote;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.common.PageResponse;
import jakarta.validation.Valid;

/** Cotações no Admin, incluindo a cotação manual (docs/api/admin.yaml). */
@RestController
@RequestMapping("/api/admin/quotes")
public class AdminQuoteController {

    private final AdminQuoteService quoteService;

    public AdminQuoteController(AdminQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping
    public PageResponse<AdminQuoteSummaryResponse> findAll(@Valid @ModelAttribute AdminQuoteFilter filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return quoteService.findAll(filter, pageable);
    }

    @PostMapping
    public ResponseEntity<AdminQuoteDetailResponse> create(@RequestBody @Valid ManualQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.create(request, AuditActor.current()));
    }

    @GetMapping("/{id}")
    public AdminQuoteDetailResponse findById(@PathVariable UUID id) {
        return quoteService.findById(id);
    }
}
