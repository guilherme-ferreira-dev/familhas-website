package br.com.familhas.crm.backup;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Backup lógico (ADR 0017).
 *
 * @param encryptionKey chave AES-256 em Base64 (32 bytes) para os backups guardados; vazia = envio por e-mail
 *                      desligado (o download direto continua)
 * @param retention     por quanto tempo um backup guardado fica disponível
 * @param defaultEmail  destino pré-preenchido do envio (JSON: voefamilhas@gmail.com)
 * @param fromEmail     remetente
 */
@ConfigurationProperties("familhas.backup")
public record BackupProperties(
        @DefaultValue("") String encryptionKey,
        @DefaultValue("P7D") Duration retention,
        @DefaultValue("voefamilhas@gmail.com") String defaultEmail,
        @DefaultValue("no-reply@familhas.tur.br") String fromEmail) {
}
