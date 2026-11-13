package br.com.familhas.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * Reservas do cliente nos status pedidos, com itens e pacote (viagens, ADR 0010). Categoria, destino e voo
     * do pacote vêm pelo batch fetch.
     */
    @EntityGraph(attributePaths = {"items", "travelPackage"})
    List<BookingEntity> findByClientIdAndStatusIn(UUID clientId, Collection<BookingStatus> statuses);

    /** Uma reserva do cliente nos status pedidos (viagem de outro cliente se comporta como inexistente). */
    @EntityGraph(attributePaths = {"items", "travelPackage"})
    Optional<BookingEntity> findByIdAndClientIdAndStatusIn(UUID id, UUID clientId, Collection<BookingStatus> statuses);

    /**
     * O cliente tem viagem passada com este pacote? Mesma regra de {@code TripTiming}: COMPLETED, ou CONFIRMED
     * sem nenhum item terminando hoje ou depois.
     */
    @Query("""
            select count(b) > 0 from BookingEntity b
             where b.client.id = :clientId
               and b.travelPackage.id = :travelPackageId
               and (b.status = br.com.familhas.booking.BookingStatus.COMPLETED
                    or (b.status = br.com.familhas.booking.BookingStatus.CONFIRMED
                        and not exists (select i.id from BookingItemEntity i
                                         where i.booking = b and i.endDate >= :today)))
            """)
    boolean existsPastTrip(@Param("clientId") UUID clientId, @Param("travelPackageId") UUID travelPackageId,
            @Param("today") LocalDate today);

    /** Pacotes com reserva não cancelada do cliente (a recomendação não sugere de novo). */
    @Query("""
            select distinct b.travelPackage.id from BookingEntity b
             where b.client.id = :clientId and b.travelPackage is not null
               and b.status <> br.com.familhas.booking.BookingStatus.CANCELLED
            """)
    List<UUID> findBookedPackageIds(@Param("clientId") UUID clientId);
}
