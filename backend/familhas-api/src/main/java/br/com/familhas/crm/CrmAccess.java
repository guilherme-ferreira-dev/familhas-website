package br.com.familhas.crm;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Checagem de permissão quando ela depende do dado (ex.: a categoria de um arquivo decide o recurso). Nas rotas
 * de permissão fixa, use {@code @PreAuthorize}. Negado = {@link AccessDeniedException} = 403.
 */
public final class CrmAccess {

    private CrmAccess() {
    }

    public static boolean can(CrmResource resource, CrmAction action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        String required = CrmPermissions.authority(resource, action);
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
    }

    public static void require(CrmResource resource, CrmAction action) {
        if (!can(resource, action)) {
            throw new AccessDeniedException("Sem permissao " + CrmPermissions.authority(resource, action));
        }
    }
}
