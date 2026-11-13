package br.com.familhas.config;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Base64UrlNamingStrategy;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.familhas.admin.payment.feed.PaymentFeedBroadcaster;
import br.com.familhas.admin.payment.feed.PaymentFeedListener;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Planilha de pagamentos em tempo quase real (ADR 0011): fila anônima desta instância ligada a payment.# no
 * exchange de eventos. Desligado com familhas.admin.payment-feed.enabled=false (testes sem broker); a planilha
 * continua funcionando pela recarga periódica.
 */
@Configuration
@ConditionalOnProperty(prefix = "familhas.admin.payment-feed", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class PaymentFeedConfig {

    static final String ROUTING_KEY = "payment.#";

    @Bean
    Queue paymentFeedQueue() {
        return new AnonymousQueue(new Base64UrlNamingStrategy("familhas-api.admin-payment-feed."));
    }

    @Bean
    Binding paymentFeedBinding(Queue paymentFeedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(paymentFeedQueue).to(eventsExchange).with(ROUTING_KEY);
    }

    @Bean
    PaymentFeedListener paymentFeedListener(PaymentFeedBroadcaster broadcaster, ObjectMapper objectMapper) {
        return new PaymentFeedListener(broadcaster, objectMapper);
    }
}
