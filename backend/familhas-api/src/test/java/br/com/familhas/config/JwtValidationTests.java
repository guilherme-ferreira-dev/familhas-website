package br.com.familhas.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Validação real (assinatura RS256, exp, issuer, audiência) com o mesmo {@link SecurityConfig#jwtValidator} usado
 * pelos decoders dos dois realms. Só a fonte das chaves é local em vez do JWKS do Keycloak.
 */
class JwtValidationTests {

    private static final String CRM = "https://localhost:8443/realms/voe-familhas-crm";
    private static final String STORE = "https://localhost:8443/realms/familhas";
    private static KeyPair keys;
    private static KeyPair otherKeys;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    @Test
    void acceptsValidCrmToken() {
        assertThat(crmDecoder().decode(sign(keys, CRM, "familhas-api", Instant.now().plusSeconds(300))).getIssuer())
                .hasToString(CRM);
    }

    @Test
    void rejectsTokenFromTheStoreRealm() {
        assertThatThrownBy(() -> crmDecoder().decode(sign(keys, STORE, "familhas-api", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class).hasMessageContaining("iss");
    }

    @Test
    void rejectsWrongAudience() {
        assertThatThrownBy(() -> crmDecoder().decode(sign(keys, CRM, "account", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class).hasMessageContaining("audiencia");
    }

    @Test
    void rejectsExpiredToken() {
        assertThatThrownBy(() -> crmDecoder().decode(sign(keys, CRM, "familhas-api", Instant.now().minusSeconds(120))))
                .isInstanceOf(JwtException.class).hasMessageContaining("expired");
    }

    @Test
    void rejectsSignatureFromAnotherKey() {
        assertThatThrownBy(() -> crmDecoder().decode(sign(otherKeys, CRM, "familhas-api", Instant.now().plusSeconds(300))))
                .isInstanceOf(JwtException.class);
    }

    private static NimbusJwtDecoder crmDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(SecurityConfig.jwtValidator(CRM, "familhas-api"));
        return decoder;
    }

    private static String sign(KeyPair pair, String issuer, String audience, Instant expiresAt) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("k1").build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .audience(List.of(audience)).issuedAt(expiresAt.minusSeconds(600)).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("k1").build(),
                claims)).getTokenValue();
    }
}
