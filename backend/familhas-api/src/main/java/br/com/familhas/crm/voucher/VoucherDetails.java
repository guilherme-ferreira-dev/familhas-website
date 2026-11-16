package br.com.familhas.crm.voucher;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados do serviço (o que a IA poderia extrair do PDF; hoje digitados). Os campos que valem para cada tipo e os
 * obrigatórios estão em {@link #missingFor(VoucherType)}.
 *
 * provider: companhia aérea, hotel, empresa de transfer ou de passeio. serviceDate/startTime: ida, check-in, saída
 * do transfer ou do passeio. endDate/endTime: volta ou check-out.
 */
public record VoucherDetails(
        @Size(max = 120) String provider,
        @Size(max = 30) String reservationCode,
        @Size(max = 120) String origin,
        @Size(max = 120) String destination,
        @Size(max = 200) String location,
        LocalDate serviceDate,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String startTime,
        LocalDate endDate,
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$") String endTime,
        @Size(max = 20) String flightNumber,
        @Size(max = 20) String returnFlightNumber,
        @Size(max = 120) String roomType,
        @Size(max = 60) String board,
        @Size(max = 60) String vehicle,
        @Size(max = 300) String description,
        @Size(max = 1000) String inclusions,
        @Min(0) @Max(20) Integer adults,
        @Min(0) @Max(20) Integer children,
        @Size(max = 10) List<@NotNull @Size(max = 150) String> passengers) {

    /** Nomes dos campos obrigatórios que faltam para o tipo (vazio = ok). */
    public List<String> missingFor(VoucherType type) {
        List<String> missing = new ArrayList<>();
        switch (type) {
            case FLIGHT -> {
                require(missing, "provider", provider);
                require(missing, "origin", origin);
                require(missing, "destination", destination);
                require(missing, "serviceDate", serviceDate);
            }
            case HOTEL -> {
                require(missing, "provider", provider);
                require(missing, "serviceDate", serviceDate);
                require(missing, "endDate", endDate);
            }
            case TRANSFER -> {
                require(missing, "origin", origin);
                require(missing, "destination", destination);
                require(missing, "serviceDate", serviceDate);
            }
            case TOUR -> {
                require(missing, "description", description);
                require(missing, "serviceDate", serviceDate);
            }
        }
        return missing;
    }

    private static void require(List<String> missing, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            missing.add(field);
        }
    }
}
