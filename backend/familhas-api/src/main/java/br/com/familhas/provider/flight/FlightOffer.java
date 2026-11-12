package br.com.familhas.provider.flight;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.common.Money;
import br.com.familhas.provider.ProviderOffer;

/** Voo de ida e volta oferecido por um provedor. Preço por passageiro, sem margem. */
public record FlightOffer(
        String offerId,
        String provider,
        String companyName,
        UUID originCityId,
        UUID destinationCityId,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        String boardingAirport,
        String returnAirport,
        Money pricePerPassenger,
        int seatsAvailable) implements ProviderOffer {
}
