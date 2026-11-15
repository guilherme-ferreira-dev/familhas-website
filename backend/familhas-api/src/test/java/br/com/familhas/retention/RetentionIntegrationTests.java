package br.com.familhas.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.booking.BookingService;
import br.com.familhas.booking.dto.BookingRequest;
import br.com.familhas.quote.QuoteService;
import br.com.familhas.quote.dto.QuoteRequest;

/**
 * Retenção no Oracle real (V17): só o que venceu sai, em lotes; cotação com reserva, evento FAILED ou PENDING e
 * linha recente ficam. Lote de 2 linhas para exercitar o laço.
 */
@SpringBootTest(properties = { "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.retention.batch-size=2" })
@Testcontainers(disabledWithoutDocker = true)
class RetentionIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID ANA = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");

    @Autowired private RetentionService retention;
    @Autowired private QuoteService quoteService;
    @Autowired private BookingService bookingService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void purgesOnlyWhatIsPastItsRetention() {
        // Outbox: 5 publicados antigos (3 lotes), 1 publicado recente, 1 FAILED antigo, 1 PENDING antigo.
        for (int i = 0; i < 5; i++) {
            outbox("PUBLISHED", 40);
        }
        UUID recentPublished = outbox("PUBLISHED", 5);
        UUID oldFailed = outbox("FAILED", 400);
        UUID oldPending = outbox("PENDING", 400);

        webhook("evt_old_1", 120);
        webhook("evt_old_2", 91);
        webhook("evt_recent", 10);

        UUID expiredOld = quote(100);
        UUID expiredRecent = quote(10);
        UUID bookedOld = quote(0);
        bookingService.create(ANA, new BookingRequest(bookedOld));
        backdateQuote(bookedOld, 200);

        Map<String, Integer> deleted = retention.purge();

        assertThat(deleted).containsEntry("outbox_event", 5)
                .containsEntry("stripe_webhook_events", 2)
                .containsEntry("quotes", 1);
        assertThat(ids("outbox_event")).contains(recentPublished, oldFailed, oldPending);
        assertThat(jdbc.queryForList("SELECT event_id FROM stripe_webhook_events", String.class))
                .contains("evt_recent").doesNotContain("evt_old_1", "evt_old_2");
        assertThat(ids("quotes")).contains(expiredRecent, bookedOld).doesNotContain(expiredOld);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quote_items WHERE quote_id = ?", Integer.class,
                uuidBytes(expiredOld))).as("itens apagados em cascata").isZero();

        // Segunda passada: nada mais a apagar.
        assertThat(retention.purge().values()).allMatch(count -> count == 0);
    }

    private UUID outbox(String status, int daysAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, routing_key, schema_version,
                    payload, status, attempts, available_at, published_at)
                VALUES (?, 'Test', 'x', 'TEST', 'test.v1', 1, '{}', ?, 1, SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY'),
                    CASE WHEN ? = 'PUBLISHED' THEN SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY') END)""",
                uuidBytes(id), status, daysAgo, status, daysAgo);
        return id;
    }

    private void webhook(String eventId, int daysAgo) {
        jdbc.update("""
                INSERT INTO stripe_webhook_events (event_id, event_type, outcome, created_at)
                VALUES (?, 'payment_intent.succeeded', 'PROCESSED', SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY'))""",
                eventId, daysAgo);
    }

    private UUID quote(int expiredDaysAgo) {
        UUID id = quoteService.create(ANA, new QuoteRequest(PACKAGE_MACEIO, 1, null, null, null)).id();
        if (expiredDaysAgo > 0) {
            backdateQuote(id, expiredDaysAgo);
        }
        return id;
    }

    private void backdateQuote(UUID id, int expiredDaysAgo) {
        jdbc.update("UPDATE quotes SET expires_at = SYSTIMESTAMP - NUMTODSINTERVAL(?, 'DAY') WHERE id = ?",
                expiredDaysAgo, uuidBytes(id));
    }

    private java.util.List<UUID> ids(String table) {
        return jdbc.query("SELECT id FROM " + table, (rs, n) -> {
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(rs.getBytes(1));
            return new UUID(buffer.getLong(), buffer.getLong());
        });
    }

    private static byte[] uuidBytes(UUID id) {
        return java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }
}
