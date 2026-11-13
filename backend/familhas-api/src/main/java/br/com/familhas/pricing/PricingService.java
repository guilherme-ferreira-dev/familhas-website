package br.com.familhas.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.familhas.common.Money;
import br.com.familhas.pricing.PriceBreakdown.PricedItem;

/**
 * Cálculo de preço de cotação e de busca (guia, Fase 5; ADR 0006).
 *
 * <ol>
 * <li>subtotal de cada item = preço unitário x quantidade;</li>
 * <li>componentsTotal = soma dos subtotais (todos na mesma moeda; não há câmbio);</li>
 * <li>margin = componentsTotal x marginPercent;</li>
 * <li>discount = (componentsTotal + margin) x promotionPercent;</li>
 * <li>serviceFee = taxa por passageiro x passageiros;</li>
 * <li>total = componentsTotal + margin - discount + serviceFee.</li>
 * </ol>
 * Todo valor é BigDecimal com escala 2 e arredondamento HALF_EVEN, aplicado a cada passo.
 */
@Service
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PricingRulesProvider rules;

    public PricingService(PricingRulesProvider rules) {
        this.rules = rules;
    }

    public PriceBreakdown price(PricingRequest request) {
        if (request.components().isEmpty()) {
            throw new PricingException("Informe ao menos um item para precificar");
        }
        if (request.passengers() < 1) {
            throw new PricingException("Informe ao menos um passageiro");
        }
        PricingProperties properties = rules.current();
        String currency = requireSingleCurrency(request.components());
        BigDecimal promotionPercent = normalizePromotion(request.promotionPercent());

        List<PricedItem> items = request.components().stream()
                .map(c -> new PricedItem(c.type(), c.description(), c.unitPrice(), c.quantity(),
                        c.unitPrice().times(c.quantity())))
                .toList();
        Money componentsTotal = items.stream().map(PricedItem::subtotal)
                .reduce(Money.zero(currency), Money::plus);
        Money margin = componentsTotal.percent(properties.marginPercent());
        Money discount = componentsTotal.plus(margin).percent(promotionPercent);
        Money serviceFee = serviceFee(properties, currency, request.passengers());
        Money total = componentsTotal.plus(margin).minus(discount).plus(serviceFee);

        return new PriceBreakdown(items, componentsTotal, properties.marginPercent(), margin, promotionPercent,
                discount, serviceFee, total);
    }

    /**
     * Preço de vitrine de um item na busca: custo + margem, sem taxa de serviço nem promoção
     * (essas entram na cotação, que considera o grupo inteiro).
     */
    public Money customerPrice(Money unitPrice, int quantity) {
        Money cost = unitPrice.times(quantity);
        return cost.plus(cost.percent(rules.current().marginPercent()));
    }

    /**
     * Desconto percentual equivalente à promoção cadastrada no pacote (price -> pricePromotion).
     * Sem promoção válida (nula, maior ou igual ao preço), 0.
     */
    public static BigDecimal promotionPercentOf(BigDecimal price, BigDecimal pricePromotion) {
        if (price == null || pricePromotion == null || price.signum() <= 0 || pricePromotion.signum() < 0
                || pricePromotion.compareTo(price) >= 0) {
            return BigDecimal.ZERO;
        }
        return price.subtract(pricePromotion).multiply(HUNDRED).divide(price, 4, RoundingMode.HALF_EVEN);
    }

    private static Money serviceFee(PricingProperties properties, String currency, int passengers) {
        BigDecimal fee = properties.serviceFeePerPassenger();
        if (fee.signum() == 0) {
            return Money.zero(currency);
        }
        Money perPassenger = new Money(fee, properties.serviceFeeCurrency());
        if (!perPassenger.currency().equals(currency)) {
            throw new PricingException("A taxa de servico e cobrada em " + perPassenger.currency()
                    + " e a cotacao esta em " + currency);
        }
        return perPassenger.times(passengers);
    }

    private static String requireSingleCurrency(List<PriceComponent> components) {
        List<String> currencies = components.stream().map(c -> c.unitPrice().currency()).distinct().sorted().toList();
        if (currencies.size() > 1) {
            throw new PricingException("Itens em moedas diferentes (" + String.join(", ", currencies)
                    + ") nao podem ser cotados juntos");
        }
        return currencies.get(0);
    }

    private static BigDecimal normalizePromotion(BigDecimal percent) {
        if (percent == null) {
            return BigDecimal.ZERO;
        }
        if (percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new PricingException("Promocao deve estar entre 0 e 100%");
        }
        return percent;
    }
}
