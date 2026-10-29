package br.com.familhas.hotel.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoomOfferRequest(
        @NotNull UUID hotelId,
        @NotBlank
        @Size(max = 120) String roomType,
        @NotNull BoardType boardType,
        @NotNull
        @Min(1)
        @Max(20) Integer maxGuests,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal pricePerNight,
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code, e.g. BRL") String currency,
        @NotNull EntityStatus status) {
}
