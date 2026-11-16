package br.com.familhas.crm.team;

import br.com.familhas.crm.CrmRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Convite: o e-mail vira o usuário; o Keycloak manda o link para definir a senha (e cadastrar TOTP, se admin). */
public record InviteRequest(@NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 80) String firstName, @NotBlank @Size(max = 80) String lastName,
        @NotNull CrmRole role) {
}
