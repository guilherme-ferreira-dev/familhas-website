package br.com.familhas.crm.instagram;

import java.time.Instant;
import java.util.UUID;

public record InstagramPostSummary(UUID id, PostFormat format, PostOrigin origin, PostStatus status, String title,
        int slideCount, boolean hasVideo, String permalink, String createdBy, Instant createdAt, Instant publishedAt) {
}
