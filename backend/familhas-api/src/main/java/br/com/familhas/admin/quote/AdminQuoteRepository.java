package br.com.familhas.admin.quote;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.familhas.quote.QuoteEntity;

/** Consultas do Admin sobre cotações (filtros dinâmicos). */
public interface AdminQuoteRepository extends JpaRepository<QuoteEntity, UUID>, JpaSpecificationExecutor<QuoteEntity> {
}
