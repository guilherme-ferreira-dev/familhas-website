package br.com.familhas.client.dto;

import br.com.familhas.enums.EntityStatus;

import java.util.List;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        EntityStatus status,
        /** true quando a conta já tem login vinculado no Keycloak. */
        boolean hasLogin,
        List<UUID> favoriteIds,
        List<UUID> myFlightIds) {

    }
