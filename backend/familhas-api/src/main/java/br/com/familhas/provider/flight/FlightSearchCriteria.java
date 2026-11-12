package br.com.familhas.provider.flight;

import java.time.LocalDate;
import java.util.UUID;

/** returnDate é opcional: sem ela, vale qualquer volta. */
public record FlightSearchCriteria(UUID originCityId, UUID destinationCityId, LocalDate departureDate,
        LocalDate returnDate, int passengers) {
}
