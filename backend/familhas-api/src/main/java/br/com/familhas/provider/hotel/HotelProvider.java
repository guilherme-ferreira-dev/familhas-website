package br.com.familhas.provider.hotel;

import br.com.familhas.provider.Provider;
import br.com.familhas.provider.ProviderType;

/** Adapters: catálogo curado, mock e, quando houver credenciais, HotelDO (ADR 0006). */
public interface HotelProvider extends Provider<HotelSearchCriteria, HotelOffer> {

    @Override
    default ProviderType type() {
        return ProviderType.HOTEL;
    }
}
