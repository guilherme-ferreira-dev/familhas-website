package br.com.familhas.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import br.com.familhas.common.Money;

/** Regras de preço da Fase 5, cobertas por casos parametrizados (valores calculados à mão). */
class PricingServiceTests {

    private static PricingService service(String marginPercent, String feePerPassenger) {
        return new PricingService(new PricingProperties(new BigDecimal(marginPercent),
                new BigDecimal(feePerPassenger), "BRL"));
    }

    /** "899.90x2;890.00x7" vira componentes em BRL. */
    private static List<PriceComponent> components(String spec) {
        return Arrays.stream(spec.split(";")).map(part -> {
            String[] unitAndQuantity = part.split("x");
            return new PriceComponent(PriceItemType.HOTEL_STAY, part, Money.of(unitAndQuantity[0], "BRL"),
                    Integer.parseInt(unitAndQuantity[1]));
        }).toList();
    }

    @ParameterizedTest(name = "[{index}] {0} margem={1}% taxa={2} pax={3} promo={4}% -> total {7}")
    @CsvSource({
            // voo 2 pax + hotel 7 noites + carro 3 dias, margem 10%
            "899.90x2;890.00x7;119.90x3, 10,   0.00, 2,   0, 838.95,   0.00, 9228.45",
            // mesma cesta com promoção de 10%: 922.845 arredonda HALF_EVEN para 922.84
            "899.90x2;890.00x7;119.90x3, 10,   0.00, 2,  10, 838.95, 922.84, 8305.61",
            // taxa de serviço de R$ 25 por passageiro
            "899.90x2;890.00x7;119.90x3, 10,  25.00, 2,   0, 838.95,   0.00, 9278.45",
            // sem margem
            "100.00x1,                    0,   0.00, 1,   0,   0.00,   0.00,  100.00",
            // margem fracionária: 124.99875 arredonda para 125.00
            "333.33x3,                 12.5,   0.00, 3,   0, 125.00,   0.00, 1124.99",
            // promoção de 100% zera o produto, mas a taxa continua
            "100.00x1,                   10,   5.00, 1, 100,  10.00, 110.00,    5.00",
            // valor mínimo: margem de 0.001 arredonda para zero
            "0.01x1,                     10,   0.00, 1,   0,   0.00,   0.00,    0.01" })
    void pricesComponentsWithMarginPromotionAndServiceFee(String spec, String marginPercent, String fee,
            int passengers, String promotion, String expectedMargin, String expectedDiscount, String expectedTotal) {
        PriceBreakdown breakdown = service(marginPercent, fee)
                .price(new PricingRequest(components(spec), passengers, new BigDecimal(promotion)));

        assertThat(breakdown.margin().amount()).isEqualTo(new BigDecimal(expectedMargin));
        assertThat(breakdown.discount().amount()).isEqualTo(new BigDecimal(expectedDiscount));
        assertThat(breakdown.total().amount()).isEqualTo(new BigDecimal(expectedTotal));
        assertThat(breakdown.currency()).isEqualTo("BRL");
        assertThat(breakdown.componentsTotal().plus(breakdown.margin()).minus(breakdown.discount())
                .plus(breakdown.serviceFee())).isEqualTo(breakdown.total());
    }

    @ParameterizedTest(name = "price={0} promo={1} -> {2}%")
    @CsvSource({
            "3299.90, 2799.90, 15.1520",
            "100.00,    75.00, 25.0000",
            "100.00,   100.00, 0",
            "100.00,   150.00, 0",
            "100.00,         , 0",
            "0,         10.00, 0" })
    void promotionPercentComesFromPackagePrices(String price, String pricePromotion, String expected) {
        BigDecimal percent = PricingService.promotionPercentOf(new BigDecimal(price),
                pricePromotion == null ? null : new BigDecimal(pricePromotion));

        assertThat(percent).isEqualByComparingTo(expected);
    }

    @Test
    void itemsAreKeptWithSubtotalsForTheQuoteSnapshot() {
        PriceBreakdown breakdown = service("10", "0").price(new PricingRequest(List.of(
                new PriceComponent(PriceItemType.FLIGHT, "Gol", Money.of("899.90", "BRL"), 2)), 2, null));

        assertThat(breakdown.items()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(PriceItemType.FLIGHT);
            assertThat(item.subtotal()).isEqualTo(Money.of("1799.80", "BRL"));
        });
        assertThat(breakdown.promotionPercent()).isEqualByComparingTo("0");
    }

    @Test
    void customerPriceIsCostPlusMarginWithoutFeeOrPromotion() {
        assertThat(service("10", "25").customerPrice(Money.of("899.90", "BRL"), 2))
                .isEqualTo(Money.of("1979.78", "BRL"));
    }

    @Test
    void mixedCurrenciesAreUnprocessable() {
        List<PriceComponent> mixed = List.of(
                new PriceComponent(PriceItemType.FLIGHT, "voo", Money.of("100", "BRL"), 1),
                new PriceComponent(PriceItemType.HOTEL_STAY, "hotel", Money.of("100", "USD"), 1));

        assertThatThrownBy(() -> service("10", "0").price(new PricingRequest(mixed, 1, null)))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("BRL, USD")
                .extracting(ex -> ((PricingException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void foreignCurrencyQuoteIsAcceptedOnlyWithoutServiceFee() {
        List<PriceComponent> usd = List.of(
                new PriceComponent(PriceItemType.HOTEL_STAY, "Orlando", Money.of("180", "USD"), 3));

        assertThat(service("10", "0").price(new PricingRequest(usd, 2, null)).total())
                .isEqualTo(Money.of("594.00", "USD"));
        assertThatThrownBy(() -> service("10", "25").price(new PricingRequest(usd, 2, null)))
                .isInstanceOf(PricingException.class);
    }

    @ParameterizedTest
    @CsvSource({ "-1", "100.01" })
    void promotionOutsideZeroToHundredIsRejected(String promotion) {
        assertThatThrownBy(() -> service("10", "0").price(new PricingRequest(components("100.00x1"), 1,
                new BigDecimal(promotion)))).isInstanceOf(PricingException.class);
    }

    @Test
    void emptyRequestOrNoPassengersIsRejected() {
        assertThatThrownBy(() -> service("10", "0").price(new PricingRequest(List.of(), 1, null)))
                .isInstanceOf(PricingException.class);
        assertThatThrownBy(() -> service("10", "0").price(new PricingRequest(components("100.00x1"), 0, null)))
                .isInstanceOf(PricingException.class);
    }
}
