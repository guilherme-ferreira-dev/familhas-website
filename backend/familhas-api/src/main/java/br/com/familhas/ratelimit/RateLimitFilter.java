package br.com.familhas.ratelimit;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting da API (ADR 0012). Fica na cadeia do Spring Security logo depois da leitura do token: quem
 * tem token é contado pelo {@code sub}, quem não tem, pelo IP. Responde 429 em Problem Details com
 * {@code code=RATE_LIMITED} e {@code Retry-After}.
 *
 * Fora do limite: webhook do Stripe (o Stripe reenvia e a assinatura já autentica), Actuator, documentação
 * OpenAPI e preflight CORS.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String CODE = "RATE_LIMITED";

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.limiter = limiter;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy policy = policyFor(request.getMethod(), request.getRequestURI());
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }
        Duration wait = limiter.tryAcquire(policy, keyFor(request));
        if (wait.isZero()) {
            chain.doFilter(request, response);
            return;
        }
        rejected(policy).increment();
        // Sem IP nem usuário no log (LGPD): a política e o correlationId (MDC) bastam para investigar.
        log.warn("Rate limit excedido: policy={} {} {}", policy, request.getMethod(), request.getRequestURI());
        long retryAfterSeconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisicoes em pouco tempo. Tente novamente em " + retryAfterSeconds + " s.");
        problem.setTitle("Too Many Requests");
        problem.setProperty("code", CODE);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    static RateLimitPolicy policyFor(String method, String path) {
        if (path == null || !path.startsWith("/api/") || "OPTIONS".equals(method)
                || path.equals("/api/webhooks/stripe")) {
            return null;
        }
        if (path.startsWith("/api/admin/") || path.startsWith("/api/crm/")) {
            return RateLimitPolicy.ADMIN;
        }
        if (path.equals("/api/me") || path.startsWith("/api/me/")) {
            return "GET".equals(method) || "HEAD".equals(method)
                    ? RateLimitPolicy.CLIENT_READ
                    : RateLimitPolicy.CLIENT_WRITE;
        }
        return RateLimitPolicy.PUBLIC;
    }

    /**
     * {@code sub} do token, senão o IP. O IP é o da conexão: atrás de proxy reverso, ligar
     * {@code server.forward-headers-strategy} para usar o X-Forwarded-For confiável (ADR 0012).
     */
    static String keyFor(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().getSubject() != null) {
            return "sub:" + jwt.getToken().getSubject();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private Counter rejected(RateLimitPolicy policy) {
        return Counter.builder("familhas.ratelimit.rejected")
                .description("Requisicoes recusadas com 429 pelo rate limit")
                .tag("policy", policy.name())
                .register(meterRegistry);
    }
}
