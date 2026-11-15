package br.com.familhas.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Guia, Fase 11 (ADR 0012): headers de segurança, rate limiting, correlationId, CORS e Actuator, com o contexto
 * Spring inteiro (filtros na ordem real). Rotas inexistentes evitam depender do banco: o filtro de rate limit
 * roda antes do controller.
 */
@SpringBootTest(properties = {
        "familhas.rate-limit.enabled=true",
        "familhas.rate-limit.public-per-minute=3",
        "familhas.rate-limit.client-write-per-minute=2",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        // Sem broker no teste: o health não pode depender do RabbitMQ local estar no ar.
        "management.health.rabbit.enabled=false" })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
class ApiHardeningTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/nao-existe").header("X-Forwarded-For", "10.0.0.1").with(remote("10.1.0.1")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void swaggerUiIsNotBlockedByTheApiCsp() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void everyResponseHasACorrelationIdAndAValidOneIsEchoed() throws Exception {
        mockMvc.perform(get("/api/nao-existe").header("X-Correlation-Id", "postman-12345678").with(remote("10.1.0.2")))
                .andExpect(header().string("X-Correlation-Id", "postman-12345678"));
        // 401 do Spring Security também leva o id: o filtro roda antes da cadeia de segurança.
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", not(nullValue())));
    }

    @Test
    void anonymousCallsAreLimitedByIpWithProblemDetails() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/nao-existe").with(remote("10.2.0.1"))).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/nao-existe").with(remote("10.2.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(header().exists("X-Correlation-Id"));
        // Outro IP tem o próprio limite.
        mockMvc.perform(get("/api/nao-existe").with(remote("10.2.0.2"))).andExpect(status().isNotFound());
    }

    @Test
    void forwardedForIsIgnoredWithoutATrustedProxy() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/nao-existe").with(remote("10.3.0.1")).header("X-Forwarded-For", "1.1.1." + i))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/nao-existe").with(remote("10.3.0.1")).header("X-Forwarded-For", "9.9.9.9"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void clientWritesAreLimitedPerUserNotPerIp() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(clientWrite("user-a", "10.4.0.1")).andExpect(status().isNotFound());
        }
        mockMvc.perform(clientWrite("user-a", "10.4.0.9")).andExpect(status().isTooManyRequests());
        mockMvc.perform(clientWrite("user-b", "10.4.0.1")).andExpect(status().isNotFound());
    }

    @Test
    void stripeWebhookIsNeverRateLimited() throws Exception {
        for (int i = 0; i < 6; i++) {
            // Sem assinatura: 400 do controller, nunca 429.
            mockMvc.perform(post("/api/webhooks/stripe").with(remote("10.5.0.1")).content("{}")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void corsAllowsAndExposesTheCorrelationHeader() throws Exception {
        mockMvc.perform(options("/api/me/quotes")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,x-correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("x-correlation-id")));
        mockMvc.perform(get("/api/nao-existe").header("Origin", "http://localhost:4200").with(remote("10.6.0.1")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-Correlation-Id")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
        mockMvc.perform(options("/api/me/quotes")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicButDetailsAndMetricsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/prometheus").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("familhas_outbox_events")))
                .andExpect(content().string(containsString("http_server_requests_seconds")));
        mockMvc.perform(get("/actuator/metrics/familhas.ratelimit.rejected")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder clientWrite(String subject, String ip) {
        return post("/api/me/nao-existe")
                .with(jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .with(remote(ip))
                .contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
