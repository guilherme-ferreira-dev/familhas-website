package br.com.familhas.crm.itinerary;

import java.time.Instant;
import java.util.UUID;

public record ItinerarySummary(UUID id, String destination, int days, String clientName, String travelPeriod,
        String createdBy, Instant updatedAt) {
}
