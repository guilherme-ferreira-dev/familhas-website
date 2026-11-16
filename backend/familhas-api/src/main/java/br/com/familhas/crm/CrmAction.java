package br.com.familhas.crm;

/** Ações sobre um {@link CrmResource}, com as chaves pedidas no prompt (ler, criar, editar...). */
public enum CrmAction {
    READ("ler"),
    CREATE("criar"),
    UPDATE("editar"),
    DELETE("excluir"),
    PUBLISH("publicar"),
    EXPORT("exportar"),
    RESTORE("restaurar");

    private final String key;

    CrmAction(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
