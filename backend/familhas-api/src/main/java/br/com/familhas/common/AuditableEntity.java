package br.com.familhas.common;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * Colunas de auditoria comuns a todas as entidades (guia, seção 6 item 4).
 * Instant em UTC, mapeado para TIMESTAMP WITH TIME ZONE no Oracle.
 */
@MappedSuperclass
@Getter
public abstract class AuditableEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
