package br.com.familhas.crm.backup;

import java.time.Instant;
import java.util.List;

/**
 * Resultado (ou prévia) da restauração, por tabela. {@code restored}: não existia e volta. {@code kept}: já existe e
 * fica como está (nunca sobrescreve edição posterior). {@code conflicts}: outro registro já usa o mesmo número ou
 * nome; ignorado.
 */
public record RestoreReport(boolean applied, Instant backupCreatedAt, String backupCreatedBy, List<TableResult> tables) {

    public record TableResult(String table, int inBackup, int restored, int kept, int conflicts) {
    }

    public int restored() {
        return tables.stream().mapToInt(TableResult::restored).sum();
    }
}
