package br.com.familhas.messaging;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabela processed_event (V1). UUID em RAW(16), como na familhas-api (ADR 0002). */
@Repository
public class ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Registra o evento para o consumidor. Retorna false se já existia (processado antes ou em andamento
     * em outra transação, que segura a linha até o commit).
     */
    public boolean tryRegister(UUID eventId, String consumer, String eventType, Instant processedAt) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO processed_event (event_id, consumer, event_type, processed_at)
                    VALUES (?, ?, ?, ?)
                    """, toBytes(eventId), consumer, eventType, Timestamp.from(processedAt));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public boolean exists(UUID eventId, String consumer) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_event WHERE event_id = ? AND consumer = ?",
                Integer.class, toBytes(eventId), consumer);
        return count != null && count > 0;
    }

    static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
