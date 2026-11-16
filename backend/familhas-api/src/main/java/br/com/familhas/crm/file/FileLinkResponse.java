package br.com.familhas.crm.file;

import java.time.Instant;

/** Caminho relativo à API (o front junta com apiBaseUrl) e quando vence. */
public record FileLinkResponse(String path, Instant expiresAt) {
}
