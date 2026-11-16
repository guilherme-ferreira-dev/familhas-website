package br.com.familhas.crm.flightsearch;

import java.time.LocalDate;
import java.util.List;

/** Fonte de preços de passagem. Hoje só a SerpApi (Google Flights); sem chave, o registry barra antes (503). */
public interface FlightSearchGateway {

    /** Opções de ida e volta, já com o preço total; lista vazia quando o Google não tem resultado. */
    List<FlightOffer> roundTrip(String origin, String destination, LocalDate departure, LocalDate returnDate, int adults);
}
