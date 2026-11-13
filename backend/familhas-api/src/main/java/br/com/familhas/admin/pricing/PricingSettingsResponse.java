package br.com.familhas.admin.pricing;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingSettingsResponse(BigDecimal marginPercent, BigDecimal serviceFeePerPassenger,
        String serviceFeeCurrency, Source source, Instant updatedAt, String updatedBy) {

    public enum Source { DATABASE, ENVIRONMENT }
}
