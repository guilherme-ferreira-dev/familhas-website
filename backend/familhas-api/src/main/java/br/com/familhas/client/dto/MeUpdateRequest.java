package br.com.familhas.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O que o próprio cliente pode alterar. E-mail, status e vínculo com o Keycloak ficam de fora:
 * e-mail pertence à identidade (Keycloak) e status é decisão do administrador.
 */
public record MeUpdateRequest(
        @NotBlank
        @Size(max = 180) String name,
        @Size(max = 40) String phoneNumber) {
}
