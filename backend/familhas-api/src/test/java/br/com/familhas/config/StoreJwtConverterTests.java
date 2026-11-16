package br.com.familhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class StoreJwtConverterTests {

    @Test
    void storeRealmRolesPassButCrmRolesAreDropped() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s")
                .claim("realm_access", Map.of("roles", List.of("ROLE_ADMIN", "ROLE_CRM_ADMIN", "crm-admin", "offline_access")))
                .build();
        assertThat(SecurityConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }
}
