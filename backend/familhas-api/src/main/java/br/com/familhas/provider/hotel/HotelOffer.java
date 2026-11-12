package br.com.familhas.provider.hotel;

import java.util.UUID;

import br.com.familhas.common.Money;
import br.com.familhas.enums.BoardType;
import br.com.familhas.provider.ProviderOffer;

/** Quarto oferecido por um provedor. Preço por quarto e por noite, sem margem. */
public record HotelOffer(
        String offerId,
        String provider,
        String hotelName,
        UUID cityId,
        int stars,
        String roomType,
        BoardType boardType,
        int maxGuests,
        Money pricePerNight) implements ProviderOffer {
}
