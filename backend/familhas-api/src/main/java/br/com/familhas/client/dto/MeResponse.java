package br.com.familhas.client.dto;

import br.com.familhas.enums.EntityStatus;

import java.util.List;
import java.util.UUID;

/** Perfil do cliente autenticado em /api/me. */
public record MeResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        EntityStatus status,
        List<String> roles,
        List<UUID> favoriteIds,
        List<UUID> myFlightIds) {
}
