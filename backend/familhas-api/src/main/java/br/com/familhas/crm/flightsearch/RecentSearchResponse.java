package br.com.familhas.crm.flightsearch;

import java.time.Instant;
import java.util.UUID;

/** Pesquisa recente para o rodapé, com os parâmetros prontos para "Refazer". */
public record RecentSearchResponse(UUID id, String summary, FlightSearchRequest request, String runBy,
        Instant lastRunAt) {
}
