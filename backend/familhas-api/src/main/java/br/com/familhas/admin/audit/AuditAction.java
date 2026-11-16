package br.com.familhas.admin.audit;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    EXPORT,
    /** Link de download de arquivo privado do CRM (ADR 0014). */
    DOWNLOAD,
    /** Publicação externa (Instagram). */
    PUBLISH,
    /** Restauração de backup lógico. */
    RESTORE,
    /** Importação de dados legados. */
    IMPORT
}
