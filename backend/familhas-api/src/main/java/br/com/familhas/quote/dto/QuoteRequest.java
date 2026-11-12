package br.com.familhas.quote.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/me/quotes. Ou travelPackageId (o pacote define voo, hotel, carro e promoção), ou itens avulsos
 * vindos da busca (offerId). Preços nunca vêm do cliente: são reconsultados no provedor.
 */
public record QuoteRequest(
        UUID travelPackageId,
        @NotNull @Min(1) @Max(9) Integer passengers,
        @Valid FlightSelection flight,
        @Valid HotelSelection hotel,
        @Valid CarSelection car) {

    public record FlightSelection(@NotBlank @Size(max = 500) String offerId) {
    }

    public record HotelSelection(
            @NotBlank @Size(max = 500) String offerId,
            @NotNull LocalDate checkIn,
            @NotNull LocalDate checkOut) {
    }

    public record CarSelection(
            @NotBlank @Size(max = 500) String offerId,
            @NotNull LocalDate pickUpDate,
            @NotNull LocalDate dropOffDate) {
    }

    public boolean hasItems() {
        return flight != null || hotel != null || car != null;
    }
}
