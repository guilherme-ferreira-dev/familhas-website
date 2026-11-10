package br.com.familhas.client.dto;

import br.com.familhas.enums.EntityStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Cadastro/edição administrativa de cliente. Senha não existe mais aqui: quem autentica é o Keycloak.
 * favoriteIds/myFlightIds ausentes (null) mantêm os atuais; lista vazia limpa.
 */
public record ClientRequest (
        @NotBlank
        @Size(max = 180)String name,
        @NotBlank
        @Email
        @Size(max = 180) String email,
        @Size(max = 40) String phoneNumber,
        @NotNull EntityStatus status,
        List<UUID> favoriteIds,
        List<UUID> myFlightIds) {
}
