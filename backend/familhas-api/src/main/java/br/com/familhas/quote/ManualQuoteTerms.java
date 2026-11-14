package br.com.familhas.quote;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Condições de uma cotação manual (Admin, ADR 0011).
 *
 * @param manualDiscount desconto em valor na moeda da cotação (0 = nenhum)
 * @param validity       validade a partir da criação; null = a da Loja
 * @param internalNote   anotação do admin, nunca mostrada ao cliente
 * @param createdBy      username do admin
 */
public record ManualQuoteTerms(BigDecimal manualDiscount, Duration validity, String internalNote, String createdBy) {
}
