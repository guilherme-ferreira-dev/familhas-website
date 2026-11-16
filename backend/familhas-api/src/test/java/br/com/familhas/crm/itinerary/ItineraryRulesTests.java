package br.com.familhas.crm.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ItineraryRulesTests {

    @Test
    void flagsTimesFlightsAirlinesAndHotelStars() {
        ItinerarySections sections = new ItinerarySections("Chegada a Maceió pela LATAM, voo LA 3456 às 08:30.",
                List.of(new ItinerarySections.Day(1, "Praia do Francês", "Saída 9h para a praia."),
                        new ItinerarySections.Day(2, "Gunga", "Hotel 5 estrelas com vista.")),
                List.of("Café da manhã", "Traslado"), "Valor em 10x ★");

        assertThat(ItineraryRules.warnings(sections))
                .anyMatch(w -> w.contains("08:30"))
                .anyMatch(w -> w.contains("9h"))
                .anyMatch(w -> w.contains("LA 3456"))
                .anyMatch(w -> w.contains("LATAM"))
                .anyMatch(w -> w.contains("5 estrelas"))
                .anyMatch(w -> w.contains("★"));
    }

    @Test
    void cleanProposalHasNoWarnings() {
        ItinerarySections sections = new ItinerarySections("Maceió tem piscinas naturais e mar verde.",
                List.of(new ItinerarySections.Day(1, "Chegada", "Recepção e acomodação no hotel.")),
                List.of("Hospedagem com café da manhã", "Passeio de buggy"), "Pagamento em PIX ou cartão em 10x.");

        assertThat(ItineraryRules.warnings(sections)).isEmpty();
    }
}
