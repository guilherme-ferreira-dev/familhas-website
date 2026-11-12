package br.com.familhas.payment;

/** Provedor de pagamento fora do ar, lento ou não configurado: 503 para o cliente. */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentGatewayException(String message) {
        super(message);
    }
}
