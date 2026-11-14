package br.com.familhas.admin.audit;

import java.lang.reflect.RecordComponent;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Num POST de /api/admin/**, guarda o "id" do recurso criado (campo id do record da resposta) para o
 * {@link AdminAuditInterceptor}. Não altera a resposta.
 */
@ControllerAdvice
public class AdminAuditResponseAdvice implements ResponseBodyAdvice<Object> {

    static final String CREATED_ID_ATTRIBUTE = AdminAuditResponseAdvice.class.getName() + ".createdId";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Record record && request instanceof ServletServerHttpRequest servletRequest
                && "POST".equals(servletRequest.getServletRequest().getMethod())
                && servletRequest.getServletRequest().getRequestURI().startsWith(AdminAuditInterceptor.ADMIN_PREFIX)) {
            String id = idOf(record);
            if (id != null) {
                servletRequest.getServletRequest().setAttribute(CREATED_ID_ATTRIBUTE, id);
            }
        }
        return body;
    }

    static String idOf(Record record) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if ("id".equals(component.getName())) {
                try {
                    Object value = component.getAccessor().invoke(record);
                    return value == null ? null : value.toString();
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
