package br.com.familhas.crm.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crm/dashboard")
public class CrmDashboardController {

    private final CrmDashboardService service;

    public CrmDashboardController(CrmDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('crm:dashboard:ler')")
    public CrmDashboardResponse dashboard() {
        return service.dashboard();
    }
}
