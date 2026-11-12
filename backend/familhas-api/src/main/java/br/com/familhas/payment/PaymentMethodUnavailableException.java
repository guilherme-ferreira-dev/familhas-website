package br.com.familhas.payment;

import org.springframework.http.HttpStatus;

import br.com.familhas.api.BusinessException;

/** Método desabilitado por configuração ou recusado pela conta do provedor: 422 PAYMENT_METHOD_UNAVAILABLE. */
public class PaymentMethodUnavailableException extends BusinessException {

    public PaymentMethodUnavailableException(PaymentMethod method) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_METHOD_UNAVAILABLE",
                "Pagamento com " + method + " nao esta disponivel no momento.");
    }
}
