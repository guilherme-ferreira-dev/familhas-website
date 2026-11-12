package br.com.familhas.provider.car;

import java.util.UUID;

import br.com.familhas.common.Money;
import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.Transmission;
import br.com.familhas.provider.ProviderOffer;

/** Carro oferecido por um provedor. Preço por dia, sem margem. */
public record CarRentalOffer(
        String offerId,
        String provider,
        UUID cityId,
        String companyName,
        String carModel,
        CarCategory carCategory,
        Transmission transmission,
        int seats,
        Money pricePerDay) implements ProviderOffer {
}
