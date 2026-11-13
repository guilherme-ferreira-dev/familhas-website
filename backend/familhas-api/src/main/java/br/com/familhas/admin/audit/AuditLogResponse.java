package br.com.familhas.admin.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, String actorSubject, String actorUsername, AuditAction action,
        String resource, String resourceId, String httpMethod, String path, int status, String details,
        Instant occurredAt) {

    static AuditLogResponse of(AuditLogEntity e) {
        return new AuditLogResponse(e.getId(), e.getActorSubject(), e.getActorUsername(), e.getAction(),
                e.getResource(), e.getResourceId(), e.getHttpMethod(), e.getPath(), e.getStatus(), e.getDetails(),
                e.getOccurredAt());
    }
}
