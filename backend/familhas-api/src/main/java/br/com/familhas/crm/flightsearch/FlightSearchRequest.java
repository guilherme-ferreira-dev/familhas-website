package br.com.familhas.crm.flightsearch;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pesquisa de passagens. EXACT: ida e volta. FLEXIBLE: até 3 meses e estadia de 1 a 30 noites (padrão 5).
 * Cada destino é cotado separadamente.
 */
public record FlightSearchRequest(
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String origin,
        @NotEmpty @Size(max = 7) List<@NotNull @Pattern(regexp = "^[A-Z]{3}$") String> destinations,
        @NotNull Mode mode,
        LocalDate departureDate,
        LocalDate returnDate,
        @Size(max = 3) List<@NotNull YearMonth> months,
        @Min(1) @Max(30) Integer stayNights,
        @Min(1) @Max(9) Integer adults) {

    public enum Mode {
        EXACT,
        FLEXIBLE
    }

    public int stay() {
        return stayNights == null ? 5 : stayNights;
    }

    public int adultCount() {
        return adults == null ? 1 : adults;
    }
}
