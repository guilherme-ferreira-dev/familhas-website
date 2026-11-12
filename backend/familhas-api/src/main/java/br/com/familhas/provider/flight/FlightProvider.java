package br.com.familhas.provider.flight;

import br.com.familhas.provider.Provider;
import br.com.familhas.provider.ProviderType;

public interface FlightProvider extends Provider<FlightSearchCriteria, FlightOffer> {

    @Override
    default ProviderType type() {
        return ProviderType.FLIGHT;
    }
}
