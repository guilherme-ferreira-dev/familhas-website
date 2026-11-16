package br.com.familhas.crm.flightsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Uma opção de ida e volta. {@code price} é o total ida+volta por adulto, na moeda informada. */
public record FlightOffer(LocalDate departureDate, LocalDate returnDate, BigDecimal price, String currency,
        List<String> airlines, int stops, Integer durationMinutes) {
}
