package br.com.familhas.crm.itinerary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.familhas.crm.file.CrmFileResponse;

/** Roteiro com os avisos do modelo mestre (horários, voos, companhias, estrelas) para a pessoa revisar. */
public record ItineraryResponse(UUID id, String destination, int days, String clientName, String clientPhone,
        String travelPeriod, String priceText, String paymentTerms, String observations, ItinerarySections sections,
        String internalAlerts, List<CrmFileResponse> photos, List<String> warnings, String createdBy,
        Instant createdAt, Instant updatedAt, long version) {
}
