package br.com.familhas.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Token de membro da equipe com as mesmas authorities que o conversor do realm do CRM gera. */
public final class CrmTestTokens {

    private CrmTestTokens() {
    }

    public static JwtRequestPostProcessor crm(CrmRole role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-" + role.name().toLowerCase())
                .claim("preferred_username", role.name().toLowerCase() + ".crm")
                .claim("realm_access", Map.of("roles", List.of(role.keycloakRole()))).build();
        return jwt().jwt(jwt).authorities(new CrmJwtGrantedAuthoritiesConverter().convert(jwt));
    }
}
