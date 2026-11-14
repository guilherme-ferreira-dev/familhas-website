package br.com.familhas.admin.quote;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.quote.dto.QuoteRequest;
import br.com.familhas.quote.dto.QuoteRequest.CarSelection;
import br.com.familhas.quote.dto.QuoteRequest.FlightSelection;
import br.com.familhas.quote.dto.QuoteRequest.HotelSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/admin/quotes. Mesma escolha da Loja (pacote ou itens avulsos), mais o cliente e as condições do
 * admin. Preços nunca vêm daqui: são reconsultados no provedor.
 */
public record ManualQuoteRequest(
        @NotNull UUID clientId,
        @NotNull @Min(1) @Max(9) Integer passengers,
        UUID travelPackageId,
        @Valid FlightSelection flight,
        @Valid HotelSelection hotel,
        @Valid CarSelection car,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal manualDiscount,
        @Min(1) @Max(720) Integer validityHours,
        @Size(max = 500) String internalNote) {

    QuoteRequest toQuoteRequest() {
        return new QuoteRequest(travelPackageId, passengers, flight, hotel, car);
    }
}
