package br.com.familhas.quote;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRepository extends JpaRepository<QuoteEntity, UUID> {

    /** Só a cotação do próprio cliente: a de outro cliente se comporta como inexistente (404). */
    @EntityGraph(attributePaths = "items")
    Optional<QuoteEntity> findByIdAndClientId(UUID id, UUID clientId);

    Page<QuoteEntity> findByClientId(UUID clientId, Pageable pageable);
}
