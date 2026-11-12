package br.com.familhas.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param promotionPercent desconto promocional de 0 a 100 (nulo = sem promoção)
 */
public record PricingRequest(List<PriceComponent> components, int passengers, BigDecimal promotionPercent) {

    public PricingRequest {
        components = components == null ? List.of() : List.copyOf(components);
    }
}
