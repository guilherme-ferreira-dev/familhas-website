package br.com.familhas.crm.flightsearch;

import java.util.List;

/** Origens fixas oferecidas e se a SerpApi está configurada. */
public record FlightSearchOptionsResponse(List<String> origins, boolean configured, int defaultStayNights,
        int maxDestinations, int maxMonths) {
}
