package br.com.familhas.admin.pricing;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PricingSettingsRequest(
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal marginPercent,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal serviceFeePerPassenger,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code, e.g. BRL")
        String serviceFeeCurrency) {
}
