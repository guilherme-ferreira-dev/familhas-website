package br.com.familhas.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    /** Só a reserva do próprio cliente: a de outro cliente se comporta como inexistente (404). */
    @EntityGraph(attributePaths = "items")
    Optional<BookingEntity> findByIdAndClientId(UUID id, UUID clientId);

    Page<BookingEntity> findByClientId(UUID clientId, Pageable pageable);

    Optional<BookingEntity> findByQuoteId(UUID quoteId);

    /**
     * Trava a reserva antes de mexer nela ou nos pagamentos dela. Toda mudança de reserva ou pagamento
     * (checkout, webhook, reconciliação) trava primeiro a reserva: a ordem fixa evita deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.id = :id")
    Optional<BookingEntity> lockById(@Param("id") UUID id);

    /** Reservas PENDING_PAYMENT com prazo vencido, para a reconciliação (só os ids; cada uma é travada depois). */
    @Query("select b.id from BookingEntity b where b.status = :status and b.expiresAt <= :now order by b.expiresAt")
    List<UUID> findIdsByStatusAndExpiresAtBefore(@Param("status") BookingStatus status, @Param("now") Instant now,
            Pageable pageable);
}
