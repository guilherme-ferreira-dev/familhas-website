package br.com.familhas.crm.privacy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de titular (acesso ou eliminação). {@code identityVerified}: a equipe confirmou que quem pede é o titular
 * (LGPD exige validar a identidade). {@code reference}: protocolo ou canal do pedido, que vai para a auditoria.
 */
public record SubjectRequest(@NotBlank @Size(max = 14) String cpf,
        @AssertTrue(message = "confirme a identidade do titular") boolean identityVerified,
        @NotBlank @Size(max = 120) String reference) {
}
