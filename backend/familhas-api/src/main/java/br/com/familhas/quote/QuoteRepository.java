package br.com.familhas.quote;

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

public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> {

    /** Só a cotação do próprio cliente: a de outro cliente se comporta como inexistente (404). */
    @EntityGraph(attributePaths = "items")
    Optional<QuoteEntity> findByIdAndClientId(UUID id, UUID clientId);

    Page<QuoteEntity> findByClientId(UUID clientId, Pageable pageable);

    /** Trava a cotação do cliente: duas reservas simultâneas da mesma cotação esperam uma pela outra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuoteEntity q where q.id = :id and q.client.id = :clientId")
    Optional<QuoteEntity> lockByIdAndClientId(@Param("id") UUID id, @Param("clientId") UUID clientId);
}
