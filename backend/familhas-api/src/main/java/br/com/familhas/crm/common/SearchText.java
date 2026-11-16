package br.com.familhas.crm.common;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Texto para busca: sem acento, minúsculo, espaços simples. "João  da SILVA" -> "joao da silva". */
public final class SearchText {

    private SearchText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** Palavras da busca, já normalizadas, sem repetição; no máximo 6 (busca rápida, não texto livre). */
    public static List<String> tokens(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" ")).distinct().limit(6).toList();
    }
}
