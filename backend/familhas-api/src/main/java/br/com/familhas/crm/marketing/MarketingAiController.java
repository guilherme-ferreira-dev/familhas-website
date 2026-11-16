package br.com.familhas.crm.marketing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.admin.audit.AuditedManually;
import jakarta.validation.Valid;

/** IA dos posts de cotação e de cruzeiros: {@code /api/crm/marketing} (ADR 0016). Consultas, não gravam. */
@RestController
@RequestMapping("/api/crm/marketing")
public class MarketingAiController {

    private final MarketingAiService service;

    public MarketingAiController(MarketingAiService service) {
        this.service = service;
    }

    @PostMapping("/extract-quote")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:posts-cotacao:criar') or hasAuthority('crm:posts-cruzeiros:criar')")
    public void extractQuote(@Valid @RequestBody QuoteSource source) {
        service.extractQuote(source);
    }

    @PostMapping("/generate-image")
    @AuditedManually
    @PreAuthorize("hasAuthority('crm:posts-cotacao:criar') or hasAuthority('crm:posts-cruzeiros:criar')")
    public void generateImage(@Valid @RequestBody ImageRequest request) {
        service.generateImage(request);
    }
}
