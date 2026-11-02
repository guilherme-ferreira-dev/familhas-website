package br.com.familhas.client.dto;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.Size;

/** Filtros de GET /clients (somente ADMIN). q busca em nome ou e-mail. */
public record ClientFilter(
        EntityStatus status,
        @Size(max = 100) String q) {

    public static ClientFilter empty() {
        return new ClientFilter(null, null);
    }
}
