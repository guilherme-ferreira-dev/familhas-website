package br.com.familhas.flight.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

public record FlightResponse(
        UUID id,
        String companyName,
        LocalDateTime boardingDate,
        LocalDateTime returnDate,
        String boardingAirport,
        String returnAirport,
        UUID originCityId,
        UUID destinationCityId,
        BigDecimal pricePerPassenger,
        String currency,
        Integer seatsAvailable,
        EntityStatus status) {
}
