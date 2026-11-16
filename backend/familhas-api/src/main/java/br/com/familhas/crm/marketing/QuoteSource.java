package br.com.familhas.crm.marketing;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Orçamento a ler: PDF enviado (categoria POST_IMAGE) ou link (cruzeiro). */
public record QuoteSource(@NotNull Kind kind, UUID fileId, @Size(max = 500) String url) {

    public enum Kind {
        PACKAGE,
        CRUISE
    }
}
