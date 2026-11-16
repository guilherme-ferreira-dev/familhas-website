package br.com.familhas.crm.file;

import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Metadados de um arquivo privado do CRM (V18). O conteúdo fica no {@link FileStorage} sob {@code storageKey}. */
@Entity
@Table(name = "crm_files")
@Getter
@Setter
@NoArgsConstructor
public class CrmFileEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private FileCategory category;

    @Column(name = "storage_key", nullable = false, length = 80, updatable = false)
    private String storageKey;

    @Column(name = "original_name", nullable = false, length = 200)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 60, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "uploaded_by", nullable = false, length = 64, updatable = false)
    private String uploadedBy;

    @Column(name = "uploaded_by_name", length = 180, updatable = false)
    private String uploadedByName;
}
