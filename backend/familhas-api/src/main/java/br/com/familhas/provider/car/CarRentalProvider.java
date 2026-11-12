package br.com.familhas.provider.car;

import br.com.familhas.provider.Provider;
import br.com.familhas.provider.ProviderType;

public interface CarRentalProvider extends Provider<CarRentalSearchCriteria, CarRentalOffer> {

    @Override
    default ProviderType type() {
        return ProviderType.CAR_RENTAL;
    }
}
