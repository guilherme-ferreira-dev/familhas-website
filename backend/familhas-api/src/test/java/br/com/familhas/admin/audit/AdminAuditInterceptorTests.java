package br.com.familhas.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;

class AdminAuditInterceptorTests {

    private final AuditService auditService = mock(AuditService.class);
    private AdminAuditInterceptor interceptor;
    private HandlerMethod handler;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        ObjectProvider<AuditService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(auditService);
        interceptor = new AdminAuditInterceptor(provider);
        Method method = Object.class.getMethod("toString");
        handler = new HandlerMethod(new Object(), method);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc-admin")
                .claim("preferred_username", "admin.familhas").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUsesTheIdFromTheResponseBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/flights");
        request.setAttribute(AdminAuditResponseAdvice.CREATED_ID_ATTRIBUTE, "f-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        interceptor.afterCompletion(request, response, handler, null);

        AuditEntry entry = captured();
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.resource()).isEqualTo("flights");
        assertThat(entry.resourceId()).isEqualTo("f-123");
        assertThat(entry.status()).isEqualTo(201);
    }

    @Test
    void updateAndDeleteUseTheIdFromThePath() {
        String id = UUID.randomUUID().toString();
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(409);

        interceptor.afterCompletion(new MockHttpServletRequest("DELETE", "/api/admin/travel-packages/" + id),
                response, handler, null);

        AuditEntry entry = captured();
        assertThat(entry.action()).isEqualTo(AuditAction.DELETE);
        assertThat(entry.resource()).isEqualTo("travel-packages");
        assertThat(entry.resourceId()).isEqualTo(id);
        assertThat(entry.status()).as("falha também fica auditada").isEqualTo(409);
        assertThat(AdminAuditInterceptor.actionOf("PUT")).isEqualTo(AuditAction.UPDATE);
    }

    @Test
    void actorComesFromTheToken() {
        interceptor.afterCompletion(new MockHttpServletRequest("PUT", "/api/admin/pricing-settings"),
                new MockHttpServletResponse(), handler, null);

        ArgumentCaptor<AuditActor> actor = ArgumentCaptor.forClass(AuditActor.class);
        verify(auditService).record(actor.capture(), any());
        assertThat(actor.getValue()).isEqualTo(new AuditActor("kc-admin", "admin.familhas"));
    }

    @Test
    void readsAndNonAdminPathsAreNotAudited() {
        interceptor.afterCompletion(new MockHttpServletRequest("GET", "/api/admin/payments"),
                new MockHttpServletResponse(), handler, null);
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/api/me/quotes"),
                new MockHttpServletResponse(), handler, null);

        verify(auditService, never()).record(any(), any());
    }

    @Test
    void unhandledExceptionIsRecordedAs500AndAuditFailureDoesNotPropagate() {
        doThrow(new IllegalStateException("db down")).when(auditService).record(any(), any());

        interceptor.afterCompletion(new MockHttpServletRequest("PUT", "/api/admin/clients/1"),
                new MockHttpServletResponse(), handler, new RuntimeException("boom"));

        assertThat(captured().status()).isEqualTo(500);
    }

    @Test
    void responseAdviceReadsTheRecordId() {
        record Created(UUID id, String name) {
        }
        UUID id = UUID.randomUUID();
        assertThat(AdminAuditResponseAdvice.idOf(new Created(id, "x"))).isEqualTo(id.toString());
    }

    private AuditEntry captured() {
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(any(), entry.capture());
        return entry.getValue();
    }
}
