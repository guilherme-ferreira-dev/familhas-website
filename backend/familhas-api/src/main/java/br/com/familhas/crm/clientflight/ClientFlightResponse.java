package br.com.familhas.crm.clientflight;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.familhas.crm.file.CrmFileResponse;

/** Voo com passageiros (CPF mascarado) e anexos. */
public record ClientFlightResponse(UUID id, String locator, String airline, String origin, String destination,
        LocalDateTime outboundAt, String outboundFlightNumber, LocalDateTime returnAt, String returnFlightNumber,
        String notes, boolean archived, Instant archivedAt, List<Traveler> travelers, List<CrmFileResponse> attachments,
        long version, String createdBy, Instant createdAt, Instant updatedAt) {

    public record Traveler(UUID id, String fullName, String cpfMasked, String phone) {
    }
}
