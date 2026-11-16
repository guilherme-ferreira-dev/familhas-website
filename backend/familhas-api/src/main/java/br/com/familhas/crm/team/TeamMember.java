package br.com.familhas.crm.team;

import java.time.Instant;
import java.util.List;

import br.com.familhas.crm.CrmRole;

/**
 * Membro da equipe. {@code pendingActions}: o que a pessoa ainda precisa fazer no primeiro acesso (definir senha,
 * cadastrar TOTP). {@code self}: é quem está logado (não pode se desativar nem trocar o próprio papel).
 */
public record TeamMember(String id, String username, String email, String firstName, String lastName, CrmRole role,
        boolean enabled, boolean totpConfigured, List<String> pendingActions, Instant createdAt, boolean self) {
}
