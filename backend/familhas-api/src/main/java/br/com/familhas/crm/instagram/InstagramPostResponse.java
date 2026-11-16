package br.com.familhas.crm.instagram;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.familhas.crm.file.CrmFileResponse;

public record InstagramPostResponse(UUID id, PostFormat format, PostOrigin origin, PostStatus status, String title,
        String idea, String caption, String hashtags, List<Slide> slides, CrmFileResponse video, String metaMediaId,
        String permalink, String lastError, Instant publishAttemptedAt, Instant publishedAt, String publishedBy,
        String createdBy, Instant createdAt, Instant updatedAt, long version) {

    public record Slide(int order, CrmFileResponse image, CrmFileResponse clean, String hook, String title,
            List<String> bullets, String caption) {
    }
}
