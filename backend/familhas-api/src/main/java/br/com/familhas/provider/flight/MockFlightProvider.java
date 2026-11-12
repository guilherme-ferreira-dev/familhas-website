package br.com.familhas.provider.flight;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.familhas.common.Money;
import br.com.familhas.provider.MockOffers;

/**
 * Voos simulados para desenvolvimento do frontend e testes, enquanto não há provedor real (Duffel/Amadeus).
 * Ligado só com familhas.providers.mock.enabled=true. O nome da companhia deixa claro que é simulado.
 */
@Component
@ConditionalOnProperty(prefix = "familhas.providers.mock", name = "enabled", havingValue = "true")
public class MockFlightProvider implements FlightProvider {

    static final int SEATS = 9;
    private static final LocalTime[] DEPARTURES = {LocalTime.of(7, 30), LocalTime.of(21, 15)};
    private static final LocalTime[] RETURNS = {LocalTime.of(19, 0), LocalTime.of(10, 45)};

    @Override
    public String name() {
        return MockOffers.NAME;
    }

    @Override
    public List<FlightOffer> search(FlightSearchCriteria criteria) {
        if (criteria.passengers() > SEATS) {
            return List.of();
        }
        LocalDate returnDate = criteria.returnDate() != null ? criteria.returnDate()
                : criteria.departureDate().plusDays(7);
        List<FlightOffer> offers = new ArrayList<>();
        for (int slot = 0; slot < DEPARTURES.length; slot++) {
            offers.add(build(criteria.originCityId(), criteria.destinationCityId(), criteria.departureDate(),
                    returnDate, slot));
        }
        return offers;
    }

    @Override
    public Optional<FlightOffer> findOffer(String offerId) {
        return MockOffers.decode(offerId, 5).flatMap(parts -> {
            try {
                return Optional.of(build(UUID.fromString(parts[0]), UUID.fromString(parts[1]),
                        LocalDate.parse(parts[2]), LocalDate.parse(parts[3]), Integer.parseInt(parts[4])));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    private static FlightOffer build(UUID origin, UUID destination, LocalDate departure, LocalDate returnDate,
            int slot) {
        if (slot < 0 || slot >= DEPARTURES.length) {
            throw new IllegalArgumentException("slot");
        }
        int base = 350 + MockOffers.stable(origin + ">" + destination, 900);
        Money price = Money.of(String.valueOf(base + slot * 120L), "BRL");
        return new FlightOffer(
                MockOffers.encode(origin.toString(), destination.toString(), departure.toString(),
                        returnDate.toString(), String.valueOf(slot)),
                MockOffers.NAME,
                "Voo simulado (mock)",
                origin,
                destination,
                departure.atTime(DEPARTURES[slot]),
                returnDate.atTime(RETURNS[slot]),
                "Aeroporto simulado",
                "Aeroporto simulado",
                price,
                SEATS);
    }
}
