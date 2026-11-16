package br.com.familhas.crm.privacy;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(UUID id, String title, String description, IncidentEntity.Severity severity,
        IncidentEntity.Status status, boolean personalData, String dataCategories, Integer affectedEstimate,
        String actionsTaken, Instant detectedAt, Instant containedAt, Instant closedAt, Instant anpdNotifiedAt,
        Instant holdersNotifiedAt, String createdBy, Instant updatedAt, long version) {

    static IncidentResponse of(IncidentEntity e) {
        return new IncidentResponse(e.getId(), e.getTitle(), e.getDescription(), e.getSeverity(), e.getStatus(),
                e.isPersonalData(), e.getDataCategories(), e.getAffectedEstimate(), e.getActionsTaken(),
                e.getDetectedAt(), e.getContainedAt(), e.getClosedAt(), e.getAnpdNotifiedAt(),
                e.getHoldersNotifiedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getVersion());
    }
}
