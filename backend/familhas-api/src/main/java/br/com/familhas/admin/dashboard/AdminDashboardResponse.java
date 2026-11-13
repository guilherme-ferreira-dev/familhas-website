package br.com.familhas.admin.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Indicadores do período (docs/api/admin.yaml, Dashboard). */
public record AdminDashboardResponse(
        LocalDate from,
        LocalDate to,
        List<Revenue> revenue,
        List<DailyRevenue> dailyRevenue,
        Map<String, Long> paymentsByStatus,
        Map<String, Long> bookingsByStatus,
        long newClients,
        long pendingBookings,
        long openQuotes) {

    public record Revenue(String currency, BigDecimal gross, BigDecimal refunded, BigDecimal net, long count) {
    }

    public record DailyRevenue(LocalDate date, String currency, BigDecimal net) {
    }
}
