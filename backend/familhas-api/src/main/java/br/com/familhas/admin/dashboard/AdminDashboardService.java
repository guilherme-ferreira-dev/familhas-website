package br.com.familhas.admin.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.admin.AdminPeriod;
import br.com.familhas.admin.dashboard.AdminDashboardResponse.DailyRevenue;
import br.com.familhas.admin.dashboard.AdminDashboardResponse.Revenue;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.payment.PaymentStatus;
import br.com.familhas.quote.QuoteStatus;
import jakarta.persistence.EntityManager;

/**
 * Indicadores do Admin (ADR 0011). Contagens agregadas no banco; a receita vem das linhas pagas do período
 * (poucas para uma agência) e é somada aqui por moeda e por dia de São Paulo.
 *
 * Receita = pagamentos aprovados no período por paidAt; líquido = valor - estornado até agora (o estorno conta no
 * dia do pagamento, não no dia do estorno).
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final List<PaymentStatus> PAID = List.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED,
            PaymentStatus.REFUNDED);

    private final EntityManager entityManager;
    private final Clock clock;

    public AdminDashboardService(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public AdminDashboardResponse dashboard(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : BusinessTime.today(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Period must be at most " + MAX_DAYS + " days");
        }
        AdminPeriod period = AdminPeriod.of(start, end);
        Instant now = clock.instant();

        List<Object[]> paid = entityManager.createQuery("""
                select p.currency, p.amount, p.refundedAmount, p.paidAt from PaymentEntity p
                 where p.paidAt >= :start and p.paidAt < :end and p.status in :statuses
                """, Object[].class)
                .setParameter("start", period.start()).setParameter("end", period.endExclusive())
                .setParameter("statuses", PAID).getResultList();

        return new AdminDashboardResponse(start, end, revenue(paid), dailyRevenue(paid),
                countByStatus("PaymentEntity", period, PaymentStatus.values()),
                countByStatus("BookingEntity", period, BookingStatus.values()),
                entityManager.createQuery("""
                        select count(c) from ClientEntity c where c.createdAt >= :start and c.createdAt < :end
                        """, Long.class)
                        .setParameter("start", period.start()).setParameter("end", period.endExclusive())
                        .getSingleResult(),
                entityManager.createQuery("""
                        select count(b) from BookingEntity b where b.status = :status and b.expiresAt > :now
                        """, Long.class)
                        .setParameter("status", BookingStatus.PENDING_PAYMENT).setParameter("now", now)
                        .getSingleResult(),
                entityManager.createQuery("""
                        select count(q) from QuoteEntity q where q.status = :status and q.expiresAt > :now
                        """, Long.class)
                        .setParameter("status", QuoteStatus.OPEN).setParameter("now", now)
                        .getSingleResult());
    }

    /** Todos os status aparecem, com 0 quando não há nenhum (o front não precisa completar). */
    private Map<String, Long> countByStatus(String entity, AdminPeriod period, Enum<?>[] statuses) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Enum<?> status : statuses) {
            counts.put(status.name(), 0L);
        }
        entityManager.createQuery("select e.status, count(e) from " + entity
                        + " e where e.createdAt >= :start and e.createdAt < :end group by e.status", Object[].class)
                .setParameter("start", period.start()).setParameter("end", period.endExclusive())
                .getResultList()
                .forEach(row -> counts.put(((Enum<?>) row[0]).name(), (Long) row[1]));
        return counts;
    }

    static List<Revenue> revenue(List<Object[]> paid) {
        Map<String, BigDecimal[]> byCurrency = new TreeMap<>();
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : paid) {
            String currency = (String) row[0];
            BigDecimal[] totals = byCurrency.computeIfAbsent(currency,
                    c -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            totals[0] = totals[0].add((BigDecimal) row[1]);
            totals[1] = totals[1].add((BigDecimal) row[2]);
            counts.merge(currency, 1L, Long::sum);
        }
        List<Revenue> result = new ArrayList<>();
        byCurrency.forEach((currency, totals) -> result.add(new Revenue(currency, scale(totals[0]), scale(totals[1]),
                scale(totals[0].subtract(totals[1])), counts.get(currency))));
        return result;
    }

    static List<DailyRevenue> dailyRevenue(List<Object[]> paid) {
        Map<LocalDate, Map<String, BigDecimal>> byDay = new TreeMap<>();
        for (Object[] row : paid) {
            LocalDate day = ((Instant) row[3]).atZone(BusinessTime.ZONE).toLocalDate();
            BigDecimal net = ((BigDecimal) row[1]).subtract((BigDecimal) row[2]);
            byDay.computeIfAbsent(day, d -> new TreeMap<>()).merge((String) row[0], net, BigDecimal::add);
        }
        List<DailyRevenue> result = new ArrayList<>();
        byDay.forEach((day, currencies) -> currencies
                .forEach((currency, net) -> result.add(new DailyRevenue(day, currency, scale(net)))));
        result.sort(Comparator.comparing(DailyRevenue::date).thenComparing(DailyRevenue::currency));
        return result;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
