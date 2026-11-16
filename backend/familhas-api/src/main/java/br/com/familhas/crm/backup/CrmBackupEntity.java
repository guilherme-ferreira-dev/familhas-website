package br.com.familhas.crm.backup;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Backup guardado (criptografado) para baixar pelo link do e-mail (V23). */
@Entity
@Table(name = "crm_backups")
@Getter
@Setter
@NoArgsConstructor
public class CrmBackupEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "storage_key", nullable = false, length = 80, updatable = false)
    private String storageKey;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "record_count", nullable = false, updatable = false)
    private int recordCount;

    @Column(name = "created_by", nullable = false, length = 180, updatable = false)
    private String createdBy;

    @Column(name = "emailed_to", length = 180)
    private String emailedTo;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
}
