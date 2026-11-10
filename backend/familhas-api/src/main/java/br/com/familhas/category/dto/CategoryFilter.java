package br.com.familhas.category.dto;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.Size;

/** Filtros de GET /categories. q busca no título. */
public record CategoryFilter(
        EntityStatus status,
        @Size(max = 100) String q) {

    public static CategoryFilter empty() {
        return new CategoryFilter(null, null);
    }

    /** Catalogo publico: mantem os filtros pedidos e forca somente itens ATIVOS. */
    public CategoryFilter activeOnly() {
        return new CategoryFilter(EntityStatus.ACTIVE, q);
    }
}
