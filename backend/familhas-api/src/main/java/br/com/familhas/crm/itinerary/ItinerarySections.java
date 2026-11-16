package br.com.familhas.crm.itinerary;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * As quatro seções do modelo mestre da proposta (JSON, "Gerar Roteiro"): apresentação do destino, roteiro dia a dia,
 * itens inclusos confirmados e investimento (com as condições de pagamento, montado de {@code priceText} e
 * {@code paymentTerms} do roteiro).
 */
public record ItinerarySections(
        @Size(max = 1500) String overview,
        @Size(max = 30) List<@Valid @NotNull Day> days,
        @Size(max = 30) List<@NotNull @Size(max = 200) String> included,
        @Size(max = 600) String investmentNotes) {

    public record Day(@Min(1) @Max(30) int day, @Size(max = 120) String title, @Size(max = 800) String description) {
    }
}
