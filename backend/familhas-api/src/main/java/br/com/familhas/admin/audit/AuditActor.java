package br.com.familhas.admin.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Quem está fazendo a ação: sub e preferred_username do token. */
public record AuditActor(String subject, String username) {

    public static AuditActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return new AuditActor(token.getToken().getSubject(), token.getToken().getClaimAsString("preferred_username"));
        }
        if (authentication != null && authentication.isAuthenticated()) {
            return new AuditActor(authentication.getName(), null);
        }
        return new AuditActor("anonymous", null);
    }

    /** Nome para mostrar: username quando existe, senão o sub. */
    public String displayName() {
        return username != null && !username.isBlank() ? username : subject;
    }
}
