package br.com.familhas.retention;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Prazos de retenção (ADR 0012). Cada prazo conta a partir do fim da utilidade da linha: publicação do evento,
 * recebimento do webhook ou vencimento da cotação.
 */
@ConfigurationProperties(prefix = "familhas.retention")
public record RetentionProperties(
        @DefaultValue("true") boolean enabled,
        /** Diário às 03:30 no fuso de São Paulo, fora do horário de uso. */
        @DefaultValue("0 30 3 * * *") String cron,
        /** Eventos PUBLISHED: só servem para investigar entrega; o consumidor já tem a própria idempotência. */
        @DefaultValue("P30D") Duration outboxPublished,
        /** Idempotência de webhook: o Stripe reenvia por até 3 dias, a margem cobre reenvio manual pelo Dashboard. */
        @DefaultValue("P90D") Duration stripeWebhookEvents,
        /** Cotação vencida e não reservada: o cliente ainda vê a cotação recente em "Minhas cotações". */
        @DefaultValue("P90D") Duration expiredQuotes,
        /** Linhas por DELETE: transações curtas, sem travar a tabela. */
        @DefaultValue("500") int batchSize) {
}
