package br.com.familhas.admin.audit;

/** O que registrar; quem fez e quando são preenchidos pelo AuditService. */
public record AuditEntry(AuditAction action, String resource, String resourceId, String httpMethod, String path,
        int status, String details) {
}
