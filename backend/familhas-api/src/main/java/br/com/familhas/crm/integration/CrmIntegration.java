package br.com.familhas.crm.integration;

/** Integrações externas do CRM. Nenhuma é simulada: sem credencial, o recurso aparece como "não configurado". */
public enum CrmIntegration {
    AI_TEXT("IA de texto e extração de PDF"),
    AI_IMAGE("IA de imagens"),
    AI_VIDEO("IA de vídeo (Reel)"),
    FLIGHT_SEARCH("Pesquisa de voos (SerpApi / Google Flights)"),
    INSTAGRAM("Publicação no Instagram (Meta Graph API)"),
    TEAM_DIRECTORY("Gestão da equipe (Admin API do Keycloak)"),
    EMAIL("Envio de e-mail (SMTP)");

    private final String label;

    CrmIntegration(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
