package br.com.familhas.admin.quote;

import java.util.UUID;

import br.com.familhas.quote.QuoteOrigin;
import br.com.familhas.quote.QuoteStatus;

/** status OPEN e EXPIRED consideram a validade no momento da consulta (EXPIRED é calculado na leitura). */
public record AdminQuoteFilter(QuoteOrigin origin, QuoteStatus status, UUID clientId) {
}
