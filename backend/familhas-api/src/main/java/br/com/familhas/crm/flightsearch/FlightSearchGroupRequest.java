package br.com.familhas.crm.flightsearch;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Grupo de destinos: nome e de 1 a 7 códigos IATA. */
public record FlightSearchGroupRequest(@NotBlank @Size(max = 60) String name,
        @NotEmpty @Size(max = 7) List<@NotNull @Pattern(regexp = "^[A-Z]{3}$") String> destinations) {
}
