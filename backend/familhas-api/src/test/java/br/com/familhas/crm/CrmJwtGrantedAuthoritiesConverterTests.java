package br.com.familhas.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class CrmJwtGrantedAuthoritiesConverterTests {

    private final CrmJwtGrantedAuthoritiesConverter converter = new CrmJwtGrantedAuthoritiesConverter();

    @Test
    void mapsCrmRolesToRoleAndPermissions() {
        assertThat(names(converter.convert(jwt(Map.of("roles", List.of("crm-atendente", "default-roles-x"))))))
                .contains("ROLE_CRM_ATENDENTE", "crm:vouchers:criar")
                .doesNotContain("crm:vouchers:excluir")
                .noneMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_USER"));
    }

    @Test
    void ignoresStoreRolesEvenIfCreatedInTheCrmRealm() {
        assertThat(converter.convert(jwt(Map.of("roles", List.of("ROLE_ADMIN", "ROLE_USER", "ROLE_CRM_ADMIN")))))
                .isEmpty();
    }

    @Test
    void combinesPermissionsOfSeveralRoles() {
        assertThat(names(converter.convert(jwt(Map.of("roles", List.of("crm-atendente", "crm-marketing"))))))
                .contains("ROLE_CRM_ATENDENTE", "ROLE_CRM_MARKETING", "crm:vouchers:criar", "crm:posts-insta:publicar");
    }

    @Test
    void noRealmAccessMeansNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s").issuedAt(Instant.now()).build();
        assertThat(converter.convert(jwt)).isEmpty();
        assertThat(converter.convert(jwt(Map.of("roles", "crm-admin")))).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("s").claim("realm_access", realmAccess).build();
    }

    private static List<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
