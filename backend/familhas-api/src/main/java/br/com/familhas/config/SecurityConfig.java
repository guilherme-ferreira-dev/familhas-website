package br.com.familhas.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.observability.CorrelationIdFilter;
import br.com.familhas.ratelimit.RateLimitFilter;
import br.com.familhas.ratelimit.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Rotas (ADR 0004):
 *   /api/**          catalogo publico, somente leitura e somente itens ATIVOS
 *   /api/admin/**    CRUD completo do catalogo e clientes (ROLE_ADMIN)
 *   /api/me/**       area do cliente autenticado (ROLE_USER ou ROLE_ADMIN)
 *   /api/webhooks/stripe POST publico, autenticado pela Stripe-Signature (ADR 0007)
 *   /actuator/health somente health e info, publico; metrics e prometheus so ADMIN (ADR 0012)
 *   /swagger-ui, /v3/api-docs documentacao OpenAPI, publica quando ligada (so no perfil local, ADR 0012)
 *
 * Headers de seguranca, rate limiting e correlationId: ADR 0012.
 */
@Configuration
public class SecurityConfig {

    static final String[] DOCS_PATHS = { "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**" };

    private final String audience;
    private final String issuerUri;
    private final String jwkSetUri;
    private final String corsAllowedOrigins;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final ObjectProvider<RateLimiter> rateLimiter;
    private final ObjectProvider<ObjectMapper> objectMapper;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public SecurityConfig(
            @Value("${familhas.security.audience:familhas-api}") String audience,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${familhas.security.cors-allowed-origins:}") String corsAllowedOrigins,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<RateLimiter> rateLimiter,
            ObjectProvider<ObjectMapper> objectMapper,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.audience = audience;
        this.issuerUri = issuerUri;
        this.jwkSetUri = jwkSetUri;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter != null) {
            // Depois da leitura do token: o limite e por usuario (sub) quando ha token, senao por IP.
            http.addFilterAfter(new RateLimitFilter(limiter, objectMapper.getIfAvailable(ObjectMapper::new),
                    meterRegistry.getIfAvailable(SimpleMeterRegistry::new)), BearerTokenAuthenticationFilter.class);
        }
        return http
                // API sem sessao nem cookie: o token vai no header Authorization, entao nao ha CSRF a explorar.
                .csrf(csrf -> csrf.disable())
                .headers(this::securityHeaders)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error")
                                .permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                        // Documentacao OpenAPI: so existe quando springdoc esta ligado (perfil local).
                        .requestMatchers(DOCS_PATHS).permitAll()
                        // Preflight do navegador: sem CORS valido o browser nem envia o token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Webhook do Stripe: sem JWT; a assinatura e validada no controller.
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/me/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        // Escrita fora de /api/admin (nao existe hoje) exige ADMIN por padrao.
                        .requestMatchers("/api/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", CorrelationIdFilter.HEADER));
        // O front pode mostrar o correlationId num erro e respeitar o Retry-After do 429.
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER, "Retry-After", "Content-Disposition"));
        configuration.setMaxAge(3600L);
        // Autenticacao por Bearer token, sem cookie: credenciais de CORS ficam desligadas.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Defaults do Spring Security (nosniff, X-Frame-Options DENY, Cache-Control no-store, HSTS em HTTPS) mais
     * CSP, Referrer-Policy e Permissions-Policy. A API so devolve JSON, entao a CSP bloqueia tudo; a Swagger UI
     * precisa carregar script e estilo e fica de fora da CSP restrita.
     */
    private void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"))
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(new OrRequestMatcher(Arrays.stream(DOCS_PATHS)
                                .map(path -> (RequestMatcher) PathPatternRequestMatcher.withDefaults().matcher(path))
                                .toList())),
                        new StaticHeadersWriter("Content-Security-Policy",
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")));
    }

    private List<String> parseOrigins() {
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank()) {
            return List.of("http://localhost:4200", "https://localhost:4200",
                    "http://localhost:4300", "https://localhost:4300");
        }
        return List.of(corsAllowedOrigins.split(",")).stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /**
     * Assinatura (JWKS), issuer e audiencia. Sem a audiencia, um token emitido para outro
     * client do mesmo realm seria aceito pela API.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator(audience)));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audienceValidator(String requiredAudience) {
        return jwt -> {
            List<String> tokenAudience = jwt.getAudience();
            if (tokenAudience != null && tokenAudience.contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Token sem a audiencia exigida", null));
        };
    }

    /** Papeis do realm (realm_access.roles) viram authorities: ROLE_USER, ROLE_ADMIN. */
    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess == null) {
                return Collections.emptyList();
            }
            Object roles = realmAccess.get("roles");
            if (!(roles instanceof Collection<?> roleCollection)) {
                return Collections.emptyList();
            }
            return roleCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> role.startsWith("ROLE_"))
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        });
        return converter;
    }
}
