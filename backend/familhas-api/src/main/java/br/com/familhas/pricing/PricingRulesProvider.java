package br.com.familhas.pricing;

/**
 * De onde vêm margem e taxa de serviço. Em produção, {@code PricingSettingsService} (tabela pricing_settings,
 * editável no Admin, com as variáveis de ambiente como padrão). Nos testes, o próprio {@link PricingProperties}.
 */
@FunctionalInterface
public interface PricingRulesProvider {

    PricingProperties current();
}
