package br.com.familhas.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.com.familhas.provider.ProviderUnavailableException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Todos os erros da API saem como Problem Details (RFC 7807, application/problem+json).
 * ResponseEntityExceptionHandler cobre os erros padrão do Spring MVC (400 de tipo, 404 de rota,
 * 405, 415...) e ResponseStatusException. Aqui ficam só os casos específicos da Familhas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 400 com a lista de campos inválidos em "errors". */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fieldErrors.merge(
                fe.getField(),
                fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                (a, b) -> a + "; " + b));

        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("Um ou mais campos sao invalidos.");
        problem.setProperty("errors", fieldErrors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Exclusão ou gravação bloqueada por restrição do banco (FK, unique).
     * Não expõe detalhes do SQL na resposta.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Operation conflicts with related records");
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** Provedor externo fora do ar, lento ou com circuito aberto (ADR 0006). */
    @ExceptionHandler(ProviderUnavailableException.class)
    public ProblemDetail handleProviderUnavailable(ProviderUnavailableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Fornecedor indisponivel no momento. Tente novamente em instantes.");
        problem.setTitle("Service Unavailable");
        problem.setProperty("provider", ex.provider());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /**
     * Erro inesperado: 500 genérico, sem stack trace na resposta. O log guarda o detalhe.
     * Exceções de segurança voltam para o filtro do Spring Security (401/403 corretos).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno. Tente novamente mais tarde.");
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
