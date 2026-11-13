package br.com.familhas.pricing;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Regras de preço (prefixo familhas.pricing). Valores provisórios até o Enéas definir as regras reais
 * (guia, seção 11; ADR 0006). Desde a Fase 10 são só o padrão: o que for salvo no Admin (pricing_settings) vale
 * mais (ADR 0011).
 *
 * @param marginPercent          margem sobre o custo dos componentes, de 0 a 100
 * @param serviceFeePerPassenger taxa de serviço por passageiro, na moeda serviceFeeCurrency
 * @param serviceFeeCurrency     moeda da taxa; cotações em outra moeda só são aceitas com taxa zero
 */
@Validated
@ConfigurationProperties(prefix = "familhas.pricing")
public record PricingProperties(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal marginPercent,
        @NotNull @DecimalMin("0") BigDecimal serviceFeePerPassenger,
        @NotBlank String serviceFeeCurrency) implements PricingRulesProvider {

    @Override
    public PricingProperties current() {
        return this;
    }
}
