package br.com.familhas.crm.file;

import java.time.Instant;
import java.util.UUID;

/** Metadados para a tela. Nunca expõe a chave do storage. */
public record CrmFileResponse(UUID id, FileCategory category, String name, String contentType, long sizeBytes,
        String sha256, String uploadedBy, Instant createdAt) {

    static CrmFileResponse of(CrmFileEntity entity) {
        return new CrmFileResponse(entity.getId(), entity.getCategory(), entity.getOriginalName(),
                entity.getContentType(), entity.getSizeBytes(), entity.getSha256(),
                entity.getUploadedByName() != null ? entity.getUploadedByName() : entity.getUploadedBy(),
                entity.getCreatedAt());
    }
}
