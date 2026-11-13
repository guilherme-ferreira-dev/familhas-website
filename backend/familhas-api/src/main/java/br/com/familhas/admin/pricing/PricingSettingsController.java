package br.com.familhas.admin.pricing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.admin.audit.AuditActor;
import jakarta.validation.Valid;

/** Margem e taxa de serviço (docs/api/admin.yaml). A alteração fica na auditoria pelo interceptor. */
@RestController
@RequestMapping("/api/admin/pricing-settings")
public class PricingSettingsController {

    private final PricingSettingsService settingsService;

    public PricingSettingsController(PricingSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public PricingSettingsResponse get() {
        return settingsService.settings();
    }

    @PutMapping
    public PricingSettingsResponse update(@RequestBody @Valid PricingSettingsRequest request) {
        return settingsService.update(request, AuditActor.current());
    }
}
