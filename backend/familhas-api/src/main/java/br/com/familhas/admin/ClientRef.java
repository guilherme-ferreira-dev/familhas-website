package br.com.familhas.admin;

import java.util.UUID;

import br.com.familhas.client.ClientEntity;

/** Cliente resumido nas linhas do Admin. */
public record ClientRef(UUID id, String name, String email) {

    public static ClientRef of(ClientEntity client) {
        return new ClientRef(client.getId(), client.getName(), client.getEmail());
    }
}
