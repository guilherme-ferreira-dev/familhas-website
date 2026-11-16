package br.com.familhas.crm.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.common.BusinessTime;
import br.com.familhas.crm.CrmAccess;
import br.com.familhas.crm.CrmAction;
import br.com.familhas.crm.CrmResource;
import br.com.familhas.crm.clientflight.ClientFlightRepository;
import br.com.familhas.crm.clientflight.ClientFlightService;
import br.com.familhas.crm.voucher.VoucherRepository;
import br.com.familhas.crm.voucher.VoucherType;

/** Números da visão geral. "Agora" é o horário de São Paulo; voos usam o horário local do bilhete. */
@Service
public class CrmDashboardService {

    static final int UPCOMING_LIMIT = 8;
    static final int MONTHS = 6;

    private final ClientFlightRepository flights;
    private final VoucherRepository vouchers;
    private final Clock clock;

    public CrmDashboardService(ClientFlightRepository flights, VoucherRepository vouchers, Clock clock) {
        this.flights = flights;
        this.vouchers = vouchers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CrmDashboardResponse dashboard() {
        return new CrmDashboardResponse(
                CrmAccess.can(CrmResource.CLIENT_FLIGHTS, CrmAction.READ) ? flightBlock() : null,
                CrmAccess.can(CrmResource.VOUCHERS, CrmAction.READ) ? voucherBlock() : null);
    }

    private CrmDashboardResponse.Flights flightBlock() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(BusinessTime.ZONE));
        YearMonth first = YearMonth.from(now);
        LocalDateTime end = first.plusMonths(MONTHS).atDay(1).atStartOfDay();
        Map<YearMonth, Long> byMonth = new LinkedHashMap<>();
        for (int i = 0; i < MONTHS; i++) {
            byMonth.put(first.plusMonths(i), 0L);
        }
        flights.outboundDates(now, end).forEach(d -> byMonth.merge(YearMonth.from(d), 1L, Long::sum));
        return new CrmDashboardResponse.Flights(
                flights.countUpcoming(now, now.plusDays(7)),
                flights.countUpcoming(now, now.plusDays(30)),
                flights.findUpcoming(now, now.plusDays(30), PageRequest.of(0, UPCOMING_LIMIT)).stream()
                        .map(ClientFlightService::summary).toList(),
                byMonth.entrySet().stream()
                        .map(e -> new CrmDashboardResponse.MonthCount(e.getKey().toString(), e.getValue())).toList());
    }

    private CrmDashboardResponse.Vouchers voucherBlock() {
        LocalDate firstDay = YearMonth.from(BusinessTime.today(clock)).atDay(1);
        Map<VoucherType, Long> byType = new EnumMap<>(VoucherType.class);
        for (VoucherType type : VoucherType.values()) {
            byType.put(type, 0L);
        }
        List<Object[]> rows = vouchers.countIssuedByType(firstDay.atStartOfDay(BusinessTime.ZONE).toInstant(),
                firstDay.plusMonths(1).atStartOfDay(BusinessTime.ZONE).toInstant());
        rows.forEach(row -> byType.put((VoucherType) row[0], ((Number) row[1]).longValue()));
        return new CrmDashboardResponse.Vouchers(byType.values().stream().mapToLong(Long::longValue).sum(), byType);
    }
}
