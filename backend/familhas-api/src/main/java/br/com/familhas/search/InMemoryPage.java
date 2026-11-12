package br.com.familhas.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import br.com.familhas.common.Pagination;
import br.com.familhas.search.dto.SearchPage;

/**
 * Ordena e pagina em memória resultados vindos de vários provedores (não há SQL único para paginar).
 * A ordenação aceita só os campos do mapa (lista branca), como no resto da API.
 */
final class InMemoryPage {

    private InMemoryPage() {
    }

    static <T> SearchPage<T> of(List<T> results, Pageable pageable, Map<String, Comparator<T>> sortable,
            String defaultSort, List<String> unavailableProviders) {
        Pagination.validateSort(pageable, sortable.keySet());
        Comparator<T> comparator = comparator(pageable.getSort(), sortable, sortable.get(defaultSort));
        List<T> sorted = results.stream().sorted(comparator).toList();

        int size = pageable.getPageSize();
        int page = pageable.getPageNumber();
        int from = (int) Math.min((long) page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) sorted.size() / size);
        return new SearchPage<>(sorted.subList(from, to), page, size, sorted.size(), totalPages,
                unavailableProviders);
    }

    private static <T> Comparator<T> comparator(Sort sort, Map<String, Comparator<T>> sortable,
            Comparator<T> fallback) {
        Comparator<T> result = null;
        for (Sort.Order order : sort) {
            Comparator<T> next = sortable.get(order.getProperty());
            if (order.isDescending()) {
                next = next.reversed();
            }
            result = result == null ? next : result.thenComparing(next);
        }
        return result == null ? fallback : result.thenComparing(fallback);
    }
}
