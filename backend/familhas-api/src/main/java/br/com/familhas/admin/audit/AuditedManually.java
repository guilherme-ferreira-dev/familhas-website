package br.com.familhas.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * POST que não é escrita e já grava a própria linha de auditoria (ex.: emitir link de download, que registra
 * DOWNLOAD). O {@link AdminAuditInterceptor} pula a rota para não duplicar o registro como CREATE.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditedManually {
}
