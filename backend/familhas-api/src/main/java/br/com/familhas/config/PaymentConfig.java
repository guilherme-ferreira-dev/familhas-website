package br.com.familhas.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import br.com.familhas.payment.PaymentProperties;
import br.com.familhas.payment.stripe.StripeProperties;

/** Pagamentos com Stripe (ADR 0007). */
@Configuration
@EnableConfigurationProperties({ PaymentProperties.class, StripeProperties.class })
public class PaymentConfig {
}
