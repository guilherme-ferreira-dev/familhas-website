package br.com.familhas.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Trava o próximo lote de pendentes. SKIP LOCKED deixa duas instâncias da API publicarem em paralelo
     * sem pegar a mesma linha. O Oracle não aceita FETCH FIRST com FOR UPDATE, por isso o limite usa ROWNUM;
     * a ordem é aproximada e nenhum consumidor depende dela.
     */
    @Query(value = """
            SELECT * FROM outbox_event
             WHERE status = 'PENDING'
               AND available_at <= :now
               AND ROWNUM <= :limit
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateId(String aggregateType, String aggregateId);
}
