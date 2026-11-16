package br.com.familhas.crm.backup;

import java.time.Instant;
import java.util.UUID;

/** Backup guardado, para a lista da tela (sem o conteúdo). */
public record StoredBackup(UUID id, long sizeBytes, int recordCount, String createdBy, String emailedTo,
        Instant createdAt, Instant expiresAt) {

    static StoredBackup of(CrmBackupEntity e) {
        return new StoredBackup(e.getId(), e.getSizeBytes(), e.getRecordCount(), e.getCreatedBy(), e.getEmailedTo(),
                e.getCreatedAt(), e.getExpiresAt());
    }
}
