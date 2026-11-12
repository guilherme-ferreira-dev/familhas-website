package br.com.familhas.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class MoneyTests {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "10.005, BRL, 10.00",
            "10.015, BRL, 10.02",
            "10.025, brl, 10.02",
            "0, USD, 0.00" })
    void amountIsNormalizedToScaleTwoWithBankersRounding(String amount, String currency, String expected) {
        Money money = Money.of(amount, currency);

        assertThat(money.amount()).isEqualTo(new BigDecimal(expected));
        assertThat(money.currency()).isEqualTo(currency.toUpperCase());
    }

    @ParameterizedTest(name = "{0} * {1}% = {2}")
    @CsvSource({
            "1000.00, 10, 100.00",
            "899.90, 10, 89.99",
            "333.33, 12.5, 41.67",
            "100.00, 0, 0.00" })
    void percentRoundsHalfEven(String amount, String percent, String expected) {
        assertThat(Money.of(amount, "BRL").percent(new BigDecimal(percent)).amount())
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    void arithmeticRequiresSameCurrency() {
        assertThat(Money.of("10", "BRL").plus(Money.of("5.50", "BRL")).amount()).isEqualTo(new BigDecimal("15.50"));
        assertThat(Money.of("10", "BRL").times(3).amount()).isEqualTo(new BigDecimal("30.00"));
        assertThatThrownBy(() -> Money.of("10", "BRL").plus(Money.of("1", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidCurrencyIsRejected() {
        assertThatThrownBy(() -> Money.of("1", "XXZ")).isInstanceOf(IllegalArgumentException.class);
    }
}
