package br.com.familhas.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * Valor monetário com moeda ISO 4217 explícita (guia, seção 6 item 5). Sempre BigDecimal, escala 2.
 * Operações entre moedas diferentes são recusadas: não há conversão de câmbio no sistema.
 */
public record Money(BigDecimal amount, String currency) {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        currency = currency.trim().toUpperCase(Locale.ROOT);
        Currency.getInstance(currency);
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(long quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    /** Percentual (ex.: 10 = 10%), arredondado HALF_EVEN em 2 casas. */
    public Money percent(BigDecimal percent) {
        return new Money(amount.multiply(percent).divide(BigDecimal.valueOf(100), SCALE, ROUNDING), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public boolean sameCurrency(Money other) {
        return currency.equals(other.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!sameCurrency(other)) {
            throw new IllegalArgumentException("Moedas diferentes: " + currency + " e " + other.currency);
        }
    }
}
