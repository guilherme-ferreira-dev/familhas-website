package br.com.familhas.crm.itinerary;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Roteiro. {@code destination}: a cidade do hotel (modelo mestre). {@code priceText}: valor informado pela equipe,
 * que vale sobre qualquer valor lido do PDF (no lugar do antigo "estilo da viagem"). {@code observations}: itens
 * obrigatórios do roteiro, até 2.000 caracteres. {@code internalAlerts}: só para a equipe.
 */
public record ItineraryRequest(
        @NotBlank @Size(max = 120) String destination,
        @Min(1) @Max(30) int days,
        @Size(max = 150) String clientName,
        @Size(max = 20) @Pattern(regexp = "^[0-9 ()+-]*$", message = "telefone invalido") String clientPhone,
        @Size(max = 120) String travelPeriod,
        @Size(max = 200) String priceText,
        @Size(max = 300) String paymentTerms,
        @Size(max = 2000) String observations,
        @NotNull @Valid ItinerarySections sections,
        @Size(max = 2000) String internalAlerts,
        @Size(max = 4) List<@NotNull UUID> photoIds,
        Long version) {
}
