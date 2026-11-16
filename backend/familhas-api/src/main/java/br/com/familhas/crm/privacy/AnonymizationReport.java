package br.com.familhas.crm.privacy;

import java.time.LocalDate;
import java.util.List;

/**
 * Resultado (ou prévia) da anonimização. Voos futuros e vouchers dentro do prazo legal ficam como estão, com o
 * motivo; nada é apagado: nome, CPF e telefone viram "Titular anonimizado" e vazio.
 */
public record AnonymizationReport(boolean applied, int travelersAnonymized, int vouchersAnonymized,
        List<Kept> kept) {

    public record Kept(String kind, String reference, String reason, LocalDate until) {
    }
}
