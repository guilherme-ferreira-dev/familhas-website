package br.com.familhas.common;

import java.util.Currency;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Moeda ISO 4217 explícita para todo valor monetário (guia, seção 6 item 5).
 */
public final class Currencies {

    public static final String DEFAULT = "BRL";

    private Currencies() {
    }

    /**
     * Normaliza o código informado. Quando ausente, mantém o atual (update) ou usa BRL (create).
     */
    public static String resolve(String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return current != null ? current : DEFAULT;
        }
        String code = requested.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 4217 currency: " + requested);
        }
        return code;
    }
}
