package br.com.familhas.crm.clientflight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientFlightRepository
        extends JpaRepository<ClientFlightEntity, UUID>, JpaSpecificationExecutor<ClientFlightEntity> {

    /** Voos ativos com ida entre os dois instantes, pela ida (dashboard). */
    @Query("""
            select f from ClientFlightEntity f
            where f.archivedAt is null and f.outboundAt >= :from and f.outboundAt < :to
            order by f.outboundAt asc
            """)
    List<ClientFlightEntity> findUpcoming(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
            select count(f) from ClientFlightEntity f
            where f.archivedAt is null and f.outboundAt >= :from and f.outboundAt < :to
            """)
    long countUpcoming(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select f.outboundAt from ClientFlightEntity f where f.archivedAt is null and f.outboundAt >= :from and f.outboundAt < :to")
    List<LocalDateTime> outboundDates(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
