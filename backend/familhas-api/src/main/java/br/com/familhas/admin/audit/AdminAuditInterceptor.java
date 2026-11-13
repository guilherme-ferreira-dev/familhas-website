package br.com.familhas.admin.audit;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Audita toda escrita em /api/admin/** (POST, PUT, PATCH, DELETE), inclusive as do catálogo que já existiam,
 * sem precisar mexer em cada controller. Roda depois da resposta pronta, com o status final (inclusive 4xx).
 *
 * O recurso é o primeiro segmento depois de /api/admin; o id é o segundo, ou, num POST, o "id" do corpo da
 * resposta (capturado por {@link AdminAuditResponseAdvice}). GETs não são auditados, exceto exportações, que o
 * próprio controller registra.
 */
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditInterceptor.class);
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    static final String ADMIN_PREFIX = "/api/admin/";

    private final ObjectProvider<AuditService> auditService;

    public AdminAuditInterceptor(ObjectProvider<AuditService> auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        if (!(handler instanceof HandlerMethod) || !WRITE_METHODS.contains(request.getMethod())) {
            return;
        }
        String path = request.getRequestURI();
        if (!path.startsWith(ADMIN_PREFIX)) {
            return;
        }
        AuditService service = auditService.getIfAvailable();
        if (service == null) {
            return;
        }
        String[] segments = path.substring(ADMIN_PREFIX.length()).split("/");
        String resourceId = segments.length > 1 ? segments[1]
                : (String) request.getAttribute(AdminAuditResponseAdvice.CREATED_ID_ATTRIBUTE);
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        try {
            service.record(AuditActor.current(), new AuditEntry(actionOf(request.getMethod()), segments[0],
                    resourceId, request.getMethod(), path, status, null));
        } catch (RuntimeException failure) {
            // A ação já foi confirmada e a resposta já saiu: não há como desfazer. Fica o log de erro.
            log.error("Falha ao gravar auditoria: method={} path={} status={}", request.getMethod(), path, status,
                    failure);
        }
    }

    static AuditAction actionOf(String method) {
        return switch (method) {
            case "POST" -> AuditAction.CREATE;
            case "DELETE" -> AuditAction.DELETE;
            default -> AuditAction.UPDATE;
        };
    }
}
