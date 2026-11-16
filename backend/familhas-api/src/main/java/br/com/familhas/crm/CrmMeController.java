package br.com.familhas.crm;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/crm/me}: perfil e permissões do membro da equipe. Não grava nada (não é cliente da Loja). */
@RestController
@RequestMapping("/api/crm/me")
public class CrmMeController {

    private static final String ROLE_PREFIX = "ROLE_CRM_";

    @GetMapping
    public CrmMeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .toList();
        return new CrmMeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("email"),
                authorities.stream().filter(a -> a.startsWith(ROLE_PREFIX)).map(a -> a.substring(ROLE_PREFIX.length()))
                        .sorted().toList(),
                authorities.stream().filter(a -> a.startsWith(CrmPermissions.PREFIX)).sorted().toList());
    }
}
