package br.com.familhas.crm.clientflight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Linha da lista de voos: sem CPF nem telefone. */
public record ClientFlightSummary(UUID id, String locator, String airline, String origin, String destination,
        LocalDateTime outboundAt, LocalDateTime returnAt, List<String> passengers, int attachmentCount,
        boolean archived) {
}
