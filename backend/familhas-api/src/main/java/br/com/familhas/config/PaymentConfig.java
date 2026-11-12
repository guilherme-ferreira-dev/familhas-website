package br.com.familhas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.familhas.payment.PaymentProperties;
import br.com.familhas.payment.PaymentReconciliationService;
import br.com.familhas.payment.stripe.StripeProperties;

/** Pagamentos com Stripe (ADR 0007). O agendamento vem do @EnableScheduling do MessagingConfig. */
@Configuration
@EnableConfigurationProperties({ PaymentProperties.class, StripeProperties.class })
public class PaymentConfig {

    /** Agenda a reconciliação (ADR 0007, D7). Desligada com familhas.payments.reconciliation-enabled=false. */
    @Component
    @ConditionalOnProperty(prefix = "familhas.payments", name = "reconciliation-enabled", havingValue = "true",
            matchIfMissing = true)
    static class PaymentReconciliationScheduler {

        private final PaymentReconciliationService reconciliation;

        PaymentReconciliationScheduler(PaymentReconciliationService reconciliation) {
            this.reconciliation = reconciliation;
        }

        @Scheduled(fixedDelayString = "${familhas.payments.reconciliation-fixed-delay:PT5M}",
                initialDelayString = "${familhas.payments.reconciliation-initial-delay:PT1M}")
        void run() {
            reconciliation.reconcile();
        }
    }
}
