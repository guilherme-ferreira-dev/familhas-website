package br.com.familhas.config;

import java.time.Clock;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.messaging.OutboxProperties;
import br.com.familhas.messaging.OutboxPublisher;

/**
 * Topologia do lado produtor e agendamento do outbox (ADR 0005).
 * As filas dos consumidores, o DLX e as DLQs são declarados por cada consumidor.
 * O RabbitAdmin do Spring declara tudo na primeira conexão, não na subida.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class MessagingConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Mesmos argumentos no familhas-email; divergência gera PRECONDITION_FAILED no broker. */
    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(MessagingTopology.EVENTS_EXCHANGE)
                .durable(true)
                .alternate(MessagingTopology.UNROUTED_EXCHANGE)
                .build();
    }

    @Bean
    FanoutExchange unroutedExchange() {
        return ExchangeBuilder.fanoutExchange(MessagingTopology.UNROUTED_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue unroutedQueue() {
        return QueueBuilder.durable(MessagingTopology.UNROUTED_QUEUE).build();
    }

    @Bean
    Binding unroutedBinding(Queue unroutedQueue, FanoutExchange unroutedExchange) {
        return BindingBuilder.bind(unroutedQueue).to(unroutedExchange);
    }

    /** Agenda o publisher. Desligado com familhas.outbox.publisher-enabled=false (testes). */
    @Component
    @ConditionalOnProperty(prefix = "familhas.outbox", name = "publisher-enabled", havingValue = "true",
            matchIfMissing = true)
    static class OutboxPublisherScheduler {

        private final OutboxPublisher publisher;

        OutboxPublisherScheduler(OutboxPublisher publisher) {
            this.publisher = publisher;
        }

        @Scheduled(fixedDelayString = "${familhas.outbox.fixed-delay:PT1S}",
                initialDelayString = "${familhas.outbox.initial-delay:PT5S}")
        void run() {
            publisher.publishPending();
        }
    }
}
