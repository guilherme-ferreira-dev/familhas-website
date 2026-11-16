package br.com.familhas.crm.integration;

/** Situação de uma integração para a tela. Nunca carrega a credencial. */
public record IntegrationStatus(CrmIntegration key, String label, boolean configured, String detail) {
}
