package br.com.familhas.crm;

import java.util.List;

import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.integration.IntegrationStatus;

/** Dados da agência e situação das integrações, para a lateral e a visão geral do CRM. */
public record CrmSettingsResponse(CompanyProperties company, List<IntegrationStatus> integrations) {
}
