package br.com.familhas.admin.audit;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Size;

public record AuditLogFilter(
        @Size(max = 100) String actor,
        @Size(max = 60) String resource,
        AuditAction action,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
}
