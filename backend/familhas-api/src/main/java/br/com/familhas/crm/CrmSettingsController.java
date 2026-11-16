package br.com.familhas.crm;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.integration.IntegrationRegistry;

/** {@code GET /api/crm/settings}: o que toda a equipe vê (dados da empresa e integrações, sem credenciais). */
@RestController
@RequestMapping("/api/crm/settings")
public class CrmSettingsController {

    private final CompanyProperties company;
    private final IntegrationRegistry integrations;

    public CrmSettingsController(CompanyProperties company, IntegrationRegistry integrations) {
        this.company = company;
        this.integrations = integrations;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:dashboard:ler')")
    public CrmSettingsResponse settings() {
        return new CrmSettingsResponse(company, integrations.statuses());
    }
}
