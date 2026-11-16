package br.com.familhas.crm.privacy;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registro de incidente. Não coloque dados pessoais na descrição: só o que aconteceu, as categorias de dados
 * afetados e a estimativa de titulares. {@code version} obrigatório na edição.
 */
public record IncidentRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull IncidentEntity.Severity severity,
        @NotNull IncidentEntity.Status status,
        boolean personalData,
        @Size(max = 300) String dataCategories,
        @Min(0) Integer affectedEstimate,
        @Size(max = 4000) String actionsTaken,
        @NotNull Instant detectedAt,
        Instant containedAt,
        Instant closedAt,
        Instant anpdNotifiedAt,
        Instant holdersNotifiedAt,
        Long version) {
}
