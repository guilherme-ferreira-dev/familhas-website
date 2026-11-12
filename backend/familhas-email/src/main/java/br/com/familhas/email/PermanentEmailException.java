package br.com.familhas.email;

/**
 * O SMTP recusou de forma definitiva (5xx, endereço inválido, autenticação) ou a mensagem não pôde ser montada.
 * Não adianta tentar de novo: vai para a DLQ. A mensagem nunca traz o endereço do destinatário.
 */
public class PermanentEmailException extends RuntimeException {

    public PermanentEmailException(String message) {
        super(message);
    }
}
