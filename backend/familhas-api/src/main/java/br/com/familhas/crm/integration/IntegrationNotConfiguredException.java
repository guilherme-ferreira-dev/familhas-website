package br.com.familhas.crm.integration;

import java.util.Map;

import org.springframework.http.HttpStatus;

import br.com.familhas.api.BusinessException;

/** O recurso depende de uma integração sem credencial ou sem provedor: 503 com code INTEGRATION_NOT_CONFIGURED. */
public class IntegrationNotConfiguredException extends BusinessException {

    public static final String CODE = "INTEGRATION_NOT_CONFIGURED";

    private final CrmIntegration integration;

    public IntegrationNotConfiguredException(CrmIntegration integration) {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, integration.label() + " nao configurada.",
                Map.of("integration", integration.name()));
        this.integration = integration;
    }

    public CrmIntegration integration() {
        return integration;
    }
}
