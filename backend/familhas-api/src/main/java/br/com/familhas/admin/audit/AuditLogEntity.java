package br.com.familhas.admin.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/** Uma ação do admin (V14). Só inserção. */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_subject", nullable = false, length = 64, updatable = false)
    private String actorSubject;

    @Column(name = "actor_username", length = 180, updatable = false)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, length = 60, updatable = false)
    private String resource;

    @Column(name = "resource_id", length = 64, updatable = false)
    private String resourceId;

    @Column(name = "http_method", nullable = false, length = 10, updatable = false)
    private String httpMethod;

    @Column(nullable = false, length = 300, updatable = false)
    private String path;

    @Column(nullable = false, updatable = false)
    private Integer status;

    @Column(length = 1000, updatable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
