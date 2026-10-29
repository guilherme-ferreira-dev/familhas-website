package br.com.familhas.common;

import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * Utilitários para filtros com Specification.
 */
public final class Specs {

    private static final char ESCAPE = '\\';

    private Specs() {
    }

    /**
     * Busca parcial, sem diferenciar maiúsculas, escapando % e _ digitados pelo usuário.
     */
    public static <T> Specification<T> containsIgnoreCase(String attribute, String text) {
        String pattern = likePattern(text);
        return (root, query, cb) -> cb.like(cb.lower(root.<String>get(attribute)), pattern, ESCAPE);
    }

    public static String likePattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static char escapeChar() {
        return ESCAPE;
    }

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
