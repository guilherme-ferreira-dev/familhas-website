package br.com.familhas.config;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/travel-packages/**",
                                "/categories/**",
                                "/cities/**",
                                "/flights/**",
                                "/hotels/**",
                                "/room-offers/**",
                                "/car-rental-offers/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/travel-packages/**",
                                "/categories/**",
                                "/cities/**",
                                "/flights/**",
                                "/hotels/**",
                                "/room-offers/**",
                                "/car-rental-offers/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/travel-packages/**",
                                "/categories/**",
                                "/cities/**",
                                "/flights/**",
                                "/hotels/**",
                                "/room-offers/**",
                                "/car-rental-offers/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/travel-packages/**",
                                "/categories/**",
                                "/cities/**",
                                "/flights/**",
                                "/hotels/**",
                                "/room-offers/**",
                                "/car-rental-offers/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/clients/**")
                        .hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/ratings/**")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .requestMatchers("/api/me")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

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
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toSet());
        });
        return converter;
    }
}
