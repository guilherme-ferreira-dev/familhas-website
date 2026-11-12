package br.com.familhas.pricing;

import java.math.BigDecimal;
import java.util.List;

import br.com.familhas.common.Money;

/**
 * Composição completa do preço, guardada como snapshot na cotação.
 * total = componentsTotal + margin - discount + serviceFee.
 */
public record PriceBreakdown(
        List<PricedItem> items,
        Money componentsTotal,
        BigDecimal marginPercent,
        Money margin,
        BigDecimal promotionPercent,
        Money discount,
        Money serviceFee,
        Money total) {

    public record PricedItem(PriceItemType type, String description, Money unitPrice, int quantity, Money subtotal) {
    }

    public String currency() {
        return total.currency();
    }
}
