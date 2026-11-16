package br.com.familhas.crm.privacy;

import java.time.Instant;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Incidente de segurança ou privacidade (V24). */
@Entity
@Table(name = "crm_incidents")
@Getter
@Setter
@NoArgsConstructor
public class IncidentEntity extends AuditableEntity {

    public enum Severity { BAIXA, MEDIA, ALTA }

    public enum Status { ABERTO, CONTIDO, ENCERRADO }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "personal_data", nullable = false)
    private boolean personalData;

    @Column(name = "data_categories", length = 300)
    private String dataCategories;

    @Column(name = "affected_estimate")
    private Integer affectedEstimate;

    @Column(name = "actions_taken", length = 4000)
    private String actionsTaken;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "contained_at")
    private Instant containedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "anpd_notified_at")
    private Instant anpdNotifiedAt;

    @Column(name = "holders_notified_at")
    private Instant holdersNotifiedAt;

    @Column(name = "created_by", nullable = false, length = 180, updatable = false)
    private String createdBy;

    @Version
    @Column(nullable = false)
    private long version;
}
