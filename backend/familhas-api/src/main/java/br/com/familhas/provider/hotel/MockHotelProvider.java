package br.com.familhas.provider.hotel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.familhas.common.Money;
import br.com.familhas.enums.BoardType;
import br.com.familhas.provider.MockOffers;

/** Hotel simulado em qualquer cidade. Ligado só com familhas.providers.mock.enabled=true. */
@Component
@ConditionalOnProperty(prefix = "familhas.providers.mock", name = "enabled", havingValue = "true")
public class MockHotelProvider implements HotelProvider {

    @Override
    public String name() {
        return MockOffers.NAME;
    }

    @Override
    public List<HotelOffer> search(HotelSearchCriteria criteria) {
        return List.of(build(criteria.cityId(), 0), build(criteria.cityId(), 1));
    }

    @Override
    public Optional<HotelOffer> findOffer(String offerId) {
        return MockOffers.decode(offerId, 2).flatMap(parts -> {
            try {
                return Optional.of(build(UUID.fromString(parts[0]), Integer.parseInt(parts[1])));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        });
    }

    private static HotelOffer build(UUID cityId, int slot) {
        if (slot != 0 && slot != 1) {
            throw new IllegalArgumentException("slot");
        }
        BigDecimal base = BigDecimal.valueOf(250L + MockOffers.stable(cityId.toString(), 200));
        boolean family = slot == 1;
        return new HotelOffer(
                MockOffers.encode(cityId.toString(), String.valueOf(slot)),
                MockOffers.NAME,
                "Hotel simulado (mock)",
                cityId,
                3,
                family ? "Suite simulada Familia" : "Quarto simulado Duplo",
                family ? BoardType.HALF_BOARD : BoardType.BREAKFAST,
                family ? 4 : 2,
                new Money(family ? base.multiply(new BigDecimal("1.8")) : base, "BRL"));
    }
}
