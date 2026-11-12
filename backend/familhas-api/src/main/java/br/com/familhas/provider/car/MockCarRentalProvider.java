package br.com.familhas.provider.car;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.familhas.common.Money;
import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.Transmission;
import br.com.familhas.provider.MockOffers;

/** Carro simulado em qualquer cidade. Ligado só com familhas.providers.mock.enabled=true. */
@Component
@ConditionalOnProperty(prefix = "familhas.providers.mock", name = "enabled", havingValue = "true")
public class MockCarRentalProvider implements CarRentalProvider {

    static final int SEATS = 5;

    @Override
    public String name() {
        return MockOffers.NAME;
    }

    @Override
    public List<CarRentalOffer> search(CarRentalSearchCriteria criteria) {
        return criteria.passengers() > SEATS ? List.of() : List.of(build(criteria.cityId()));
    }

    @Override
    public Optional<CarRentalOffer> findOffer(String offerId) {
        return MockOffers.decode(offerId, 1).flatMap(parts -> {
            try {
                return Optional.of(build(UUID.fromString(parts[0])));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    private static CarRentalOffer build(UUID cityId) {
        return new CarRentalOffer(
                MockOffers.encode(cityId.toString()),
                MockOffers.NAME,
                cityId,
                "Locadora simulada (mock)",
                "Carro simulado",
                CarCategory.ECONOMY,
                Transmission.MANUAL,
                SEATS,
                Money.of(String.valueOf(90 + MockOffers.stable(cityId.toString(), 60)), "BRL"));
    }
}
