package br.com.familhas.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.familhas.admin.audit.AdminAuditInterceptor;
import br.com.familhas.admin.audit.AuditService;

/**
 * Auditoria das escritas em /api/admin/** (ADR 0011). O AuditService vem por ObjectProvider: nos testes de
 * fatia (WebMvcTest) ele não existe e a auditoria simplesmente não grava.
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuditService> auditService;

    public AdminWebConfig(ObjectProvider<AuditService> auditService) {
        this.auditService = auditService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuditInterceptor(auditService)).addPathPatterns("/api/admin/**");
    }
}
