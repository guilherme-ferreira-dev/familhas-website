package br.com.familhas.messaging;

/**
 * Payload que não pode ser processado nunca (JSON quebrado, campo obrigatório faltando, formato inválido).
 * Falha permanente: vai direto para a DLQ, sem retry. A mensagem cita só nomes de campos, nunca valores.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
