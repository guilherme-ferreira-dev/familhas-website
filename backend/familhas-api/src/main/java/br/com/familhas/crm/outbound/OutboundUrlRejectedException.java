package br.com.familhas.crm.outbound;

import org.springframework.http.HttpStatus;

import br.com.familhas.api.BusinessException;

/** URL externa recusada pela política anti-SSRF. O motivo detalhado fica só no log. */
public class OutboundUrlRejectedException extends BusinessException {

    public OutboundUrlRejectedException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "URL_NOT_ALLOWED", "Endereco nao permitido.");
        this.reason = reason;
    }

    private final String reason;

    public String reason() {
        return reason;
    }
}
