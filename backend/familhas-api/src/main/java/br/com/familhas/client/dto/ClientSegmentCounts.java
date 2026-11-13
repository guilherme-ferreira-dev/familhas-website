package br.com.familhas.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Quantos clientes há em cada aba do Admin. */
public record ClientSegmentCounts(long all, @JsonProperty("new") long newClients, long active, long past) {
}
