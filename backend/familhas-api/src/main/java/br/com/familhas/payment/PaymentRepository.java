package br.com.familhas.payment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    /** Tentativas da reserva, da mais recente para a mais antiga. */
    List<PaymentEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<PaymentEntity> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<PaymentEntity> findByStripeCheckoutSessionId(String sessionId);

    Optional<PaymentEntity> findFirstByStripePaymentIntentId(String paymentIntentId);

    /** Pagamento mais recente de cada reserva da página (lista "Minhas reservas", sem N+1). */
    @Query("""
            select p from PaymentEntity p
             where p.booking.id in :bookingIds
               and p.createdAt = (select max(p2.createdAt) from PaymentEntity p2 where p2.booking.id = p.booking.id)
            """)
    List<PaymentEntity> findLatestByBookingIds(@Param("bookingIds") Collection<UUID> bookingIds);

    /** Reconciliação: tentativas sem novidade há algum tempo (só os ids; cada uma é tratada com a reserva travada). */
    @Query("select p.id from PaymentEntity p where p.status in :statuses and p.updatedAt <= :before order by p.updatedAt")
    List<UUID> findStaleIds(@Param("statuses") Collection<PaymentStatus> statuses, @Param("before") Instant before,
            Pageable pageable);

    /** Todas as tentativas das reservas, com as parcelas (viagens do cliente, sem N+1). */
    @EntityGraph(attributePaths = "installmentPlan")
    List<PaymentEntity> findByBookingIdIn(Collection<UUID> bookingIds);
}
