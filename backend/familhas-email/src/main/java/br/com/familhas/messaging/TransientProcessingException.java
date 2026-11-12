package br.com.familhas.messaging;

/**
 * Falha que pode passar sozinha (SMTP fora do ar, timeout, 4xx). É a única exceção que o listener
 * tenta de novo, com backoff; esgotadas as tentativas, a mensagem vai para a DLQ.
 * Toda outra exceção é tratada como permanente.
 */
public class TransientProcessingException extends RuntimeException {

    public TransientProcessingException(String message) {
        super(message);
    }
}
