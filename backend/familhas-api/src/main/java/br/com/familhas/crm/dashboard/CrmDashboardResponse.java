package br.com.familhas.crm.dashboard;

import java.util.List;
import java.util.Map;

import br.com.familhas.crm.clientflight.ClientFlightSummary;
import br.com.familhas.crm.voucher.VoucherType;

/**
 * Visão geral operacional (JSON: voos próximos e gráficos; sem faturas). Blocos de módulo que a pessoa não pode
 * ler vêm null: o painel não vaza nomes de passageiros para quem não vê Voos Clientes.
 */
public record CrmDashboardResponse(Flights flights, Vouchers vouchers) {

    public record Flights(long next7Days, long next30Days, List<ClientFlightSummary> upcoming,
            List<MonthCount> byMonth) {
    }

    public record Vouchers(long issuedThisMonth, Map<VoucherType, Long> byTypeThisMonth) {
    }

    /** {@code month} no formato 2026-12. */
    public record MonthCount(String month, long count) {
    }
}
