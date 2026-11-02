package br.com.familhas.hotel.dto;

import java.util.UUID;

import br.com.familhas.enums.EntityStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @NotBlank
        @Size(max = 180) String name,
        @NotNull UUID cityId,
        @Size(max = 300) String address,
        @NotNull
        @Min(1)
        @Max(5) Integer stars,
        @Size(max = 1000) String description,
        @Size(max = 500) String imageUrl,
        @NotNull EntityStatus status) {
}
