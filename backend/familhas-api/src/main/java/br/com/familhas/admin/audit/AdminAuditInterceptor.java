package br.com.familhas.admin.audit;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Audita toda escrita em /api/admin/** e /api/crm/** (POST, PUT, PATCH, DELETE), inclusive as do catálogo que já
 * existiam, sem precisar mexer em cada controller. No CRM o recurso ganha o prefixo "crm/" (ex.: crm/files). Roda depois da resposta pronta, com o status final (inclusive 4xx).
 *
 * O recurso é o primeiro segmento depois de /api/admin; o id é o segundo, ou, num POST, o "id" do corpo da
 * resposta (capturado por {@link AdminAuditResponseAdvice}). GETs não são auditados, exceto exportações, que o
 * próprio controller registra.
 */
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditInterceptor.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    static final String ADMIN_PREFIX = "/api/admin/";
    static final String CRM_PREFIX = "/api/crm/";

    private final ObjectProvider<AuditService> auditService;

    public AdminAuditInterceptor(ObjectProvider<AuditService> auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (!(handler instanceof HandlerMethod method) || !WRITE_METHODS.contains(request.getMethod())
                || method.hasMethodAnnotation(AuditedManually.class)) {
            return;
        }
        String path = request.getRequestURI();
        String prefix = prefixOf(path);
        if (prefix == null) {
            return;
        }
        AuditService service = auditService.getIfAvailable();
        if (service == null) {
            return;
        }
        String[] segments = path.substring(prefix.length()).split("/");
        String resourceId = segments.length > 1 ? segments[1]
                : (String) request.getAttribute(AdminAuditResponseAdvice.CREATED_ID_ATTRIBUTE);
        int status = statusOf(response.getStatus(), ex);
        try {
            String resource = CRM_PREFIX.equals(prefix) ? "crm/" + segments[0] : segments[0];
            service.record(AuditActor.current(), new AuditEntry(actionOf(request.getMethod()), resource,
                    resourceId, request.getMethod(), path, status, null));
        } catch (RuntimeException failure) {
            // A ação já foi confirmada e a resposta já saiu: não há como desfazer. Fica o log de erro.
            log.error("Falha ao gravar auditoria: method={} path={} status={}", request.getMethod(), path, status,
                    failure);
        }
    }

    /**
     * Status final. Exceção de segurança lançada no service (ex.: permissão do CRM negada por {@code CrmAccess}) ainda
     * não virou 403/401 quando o interceptor roda: o filtro do Spring Security converte depois.
     */
    static int statusOf(int responseStatus, Exception ex) {
        if (ex instanceof AccessDeniedException) {
            return 403;
        }
        if (ex instanceof AuthenticationException) {
            return 401;
        }
        return ex != null && responseStatus < 400 ? 500 : responseStatus;
    }

    /** Prefixo auditado da rota, ou null. */
    static String prefixOf(String path) {
        if (path.startsWith(ADMIN_PREFIX)) {
            return ADMIN_PREFIX;
        }
        return path.startsWith(CRM_PREFIX) ? CRM_PREFIX : null;
    }

    static AuditAction actionOf(String method) {
        return switch (method) {
            case "POST" -> AuditAction.CREATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> AuditAction.UPDATE;
        };
    }
}
