package br.com.familhas.messaging;

import br.com.familhas.client.ClientEntity;

/**
 * Destinatário dos e-mails disparados por eventos: e-mail e primeiro nome do cliente (dados pessoais).
 * Vão no corpo porque o consumidor precisa deles, mas nunca em log, header ou toString (docs/events, LGPD).
 */
public record EventRecipient(String email, String firstName) {

    public static final int FIRST_NAME_MAX_LENGTH = 100;

    public static EventRecipient of(ClientEntity client) {
        return new EventRecipient(client.getEmail(), firstNameOf(client.getName()));
    }

    static String firstNameOf(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String first = name.trim().split("\\s+")[0];
        return first.length() > FIRST_NAME_MAX_LENGTH ? first.substring(0, FIRST_NAME_MAX_LENGTH) : first;
    }
}
