package br.com.familhas.crm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authorities de um token do realm do CRM: só os papéis conhecidos de {@link CrmRole} contam. Um papel
 * desconhecido (ou um {@code ROLE_ADMIN} criado por engano neste realm) é ignorado: a lista é fechada.
 */
public class CrmJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String role : realmRoles(jwt)) {
            CrmRole.fromKeycloakRole(role).ifPresent(crmRole -> {
                authorities.add(new SimpleGrantedAuthority(crmRole.authority()));
                crmRole.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
            });
        }
        return authorities;
    }

    public static Collection<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
