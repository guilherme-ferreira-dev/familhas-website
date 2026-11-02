package br.com.familhas.common;

import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de paginação compartilhadas.
 * A ordenação aceita só campos da lista branca de cada recurso: evita 500 com campo inexistente
 * e impede ordenar por atributos internos (ex.: clients.password).
 */
public final class Pagination {

    private Pagination() {
    }

    public static void validateSort(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid sort property '" + order.getProperty() + "'. Allowed: "
                                + String.join(", ", allowedProperties.stream().sorted().toList()));
            }
        }
    }
}
