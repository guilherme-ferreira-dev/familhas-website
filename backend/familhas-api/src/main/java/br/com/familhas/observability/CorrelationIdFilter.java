package br.com.familhas.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.familhas.messaging.OutboxService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * correlationId de cada requisição HTTP (ADR 0012).
 *
 * Aceita o header {@code X-Correlation-Id} do chamador só se for curto e sem caracteres especiais (nada de
 * quebra de linha ou JSON forjado no log); senão gera um UUID. O valor vai para o MDC (aparece em toda linha
 * de log da requisição e no outbox, de onde segue para o familhas-email) e volta no header da resposta.
 *
 * Registrado antes do Spring Security (ver ObservabilityConfig): 401, 403 e 429 também saem com o id.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = OutboxService.CORRELATION_ID_MDC_KEY;
    public static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    /** Cabe na coluna outbox_event.correlation_id (64). */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Dispatch de erro ou assíncrono: reaproveita o id da primeira passagem.
        String correlationId = request.getAttribute(ATTRIBUTE) instanceof String existing
                ? existing
                : resolve(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, correlationId);
        MDC.put(MDC_KEY, correlationId);
        if (!response.isCommitted()) {
            response.setHeader(HEADER, correlationId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String header) {
        if (header != null && VALID.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    /** Também nos dispatches de erro e assíncronos (exportação, SSE), que rodam em outra passagem pelo filtro. */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
