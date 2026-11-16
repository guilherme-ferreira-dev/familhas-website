package br.com.familhas.crm.flightsearch;

import java.util.List;

/** Resultado por destino, do mais barato para o mais caro. {@code error} quando aquele destino falhou. */
public record FlightSearchResponse(FlightSearchRequest request, List<DestinationResult> destinations) {

    public record DestinationResult(String destination, List<FlightOffer> offers, String error) {
    }
}
