package br.com.familhas.crm.backup;

import java.util.UUID;

/** Acesso de teste à conversão UUID -> RAW(16) usada na restauração. */
public final class BackupServiceAccess {

    private BackupServiceAccess() {
    }

    public static byte[] raw(UUID id) {
        return BackupService.raw(id);
    }
}
