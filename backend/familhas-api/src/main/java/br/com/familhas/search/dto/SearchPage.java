package br.com.familhas.search.dto;

import java.util.List;

/**
 * Página de resultados de busca. Mesmo formato do PageResponse, mais os provedores que não responderam
 * (resultado parcial: a busca continua valendo com quem respondeu).
 */
public record SearchPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<String> unavailableProviders) {
}
