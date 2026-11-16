package br.com.familhas.crm.clientflight;

import java.time.LocalDate;

/** Filtros da lista: busca rápida (q), arquivados, período da ida e companhia. */
public record ClientFlightFilter(String q, boolean archived, LocalDate from, LocalDate to, String airline) {
}
