package br.com.familhas.crm;

/** Recursos do CRM (módulos do JSON). A chave compõe a permissão {@code crm:<recurso>:<ação>} (ADR 0013). */
public enum CrmResource {
    DASHBOARD("dashboard"),
    CLIENT_FLIGHTS("voos-clientes"),
    FLIGHT_SEARCH("pesquisa-voos"),
    VOUCHERS("vouchers"),
    QUOTE_POSTS("posts-cotacao"),
    CRUISE_POSTS("posts-cruzeiros"),
    SAVED_IMAGES("imagens"),
    INSTAGRAM_POSTS("posts-insta"),
    ITINERARIES("roteiros"),
    TEAM("equipe"),
    DATA_IMPORT("importacao"),
    DOCUMENTS("documentos"),
    BACKUP("backup"),
    AUDIT("auditoria"),
    /** LGPD: relatório e anonimização de titular, registro de incidentes (ADR 0018). Só admin. */
    PRIVACY("privacidade");

    private final String key;

    CrmResource(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
