package br.com.familhas.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Regra de negócio violada, com um {@code code} estável no Problem Details para o front decidir a mensagem
 * (docs/api/checkout.yaml, CodedProblem). Tratada pelo ResponseEntityExceptionHandler como qualquer
 * ErrorResponseException.
 */
public class BusinessException extends ErrorResponseException {

    private final String code;

    public BusinessException(HttpStatus status, String code, String detail) {
        this(status, code, detail, Map.of());
    }

    public BusinessException(HttpStatus status, String code, String detail, Map<String, Object> properties) {
        super(status, problem(status, code, detail, properties), null);
        this.code = code;
    }

    public String code() {
        return code;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail,
            Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        properties.forEach(problem::setProperty);
        return problem;
    }
}
