package br.com.familhas.crm.marketing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Imagem por IA: cidade da hospedagem (posts de cotação) ou navio (cruzeiros). */
public record ImageRequest(@NotNull Subject subject, @NotBlank @Size(max = 120) String name) {

    public enum Subject {
        CITY,
        SHIP
    }
}
