package br.com.familhas.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.admin.dashboard.AdminDashboardResponse.DailyRevenue;
import br.com.familhas.admin.dashboard.AdminDashboardResponse.Revenue;
import jakarta.persistence.EntityManager;

class AdminDashboardServiceTests {

    @Test
    void revenueIsGroupedByCurrencyWithRefundsDiscounted() {
        List<Object[]> paid = List.of(
                row("BRL", "1000.00", "0.00", "2027-01-10T15:00:00Z"),
                row("BRL", "500.00", "200.00", "2027-01-10T16:00:00Z"),
                row("USD", "80.00", "80.00", "2027-01-11T12:00:00Z"));

        assertThat(AdminDashboardService.revenue(paid)).containsExactly(
                new Revenue("BRL", new BigDecimal("1500.00"), new BigDecimal("200.00"), new BigDecimal("1300.00"), 2),
                new Revenue("USD", new BigDecimal("80.00"), new BigDecimal("80.00"), new BigDecimal("0.00"), 1));
    }

    @Test
    void dailyRevenueUsesTheSaoPauloDay() {
        // 02:30 UTC de 11/01 ainda é 10/01 em São Paulo (UTC-3).
        List<Object[]> paid = List.of(
                row("BRL", "100.00", "0.00", "2027-01-11T02:30:00Z"),
                row("BRL", "50.00", "0.00", "2027-01-11T03:30:00Z"));

        assertThat(AdminDashboardService.dailyRevenue(paid)).containsExactly(
                new DailyRevenue(LocalDate.of(2027, 1, 10), "BRL", new BigDecimal("100.00")),
                new DailyRevenue(LocalDate.of(2027, 1, 11), "BRL", new BigDecimal("50.00")));
    }

    @Test
    void rejectsInvertedOrTooLongPeriods() {
        AdminDashboardService service = new AdminDashboardService(mock(EntityManager.class),
                Clock.fixed(Instant.parse("2027-01-15T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.dashboard(LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 1)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.dashboard(LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1)))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static Object[] row(String currency, String amount, String refunded, String paidAt) {
        return new Object[] { currency, new BigDecimal(amount), new BigDecimal(refunded), Instant.parse(paidAt) };
    }
}
