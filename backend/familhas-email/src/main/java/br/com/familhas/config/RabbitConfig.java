package br.com.familhas.config;

import java.time.Clock;
import java.util.Map;

import org.aopalliance.aop.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import br.com.familhas.email.EmailProperties;
import br.com.familhas.messaging.EventLogContext;
import br.com.familhas.messaging.MessagingTopology;
import br.com.familhas.messaging.TransientProcessingException;

/**
 * Topologia do consumidor, retry e DLQ (ADR 0005).
 *
 * - Só {@link TransientProcessingException} é tentada de novo, com backoff exponencial.
 * - Payload inválido e falha permanente vão direto para a DLQ; transitória também vai, quando acabam as tentativas.
 * - "Ir para a DLQ" = reject sem requeue; o RabbitMQ encaminha pelo x-dead-letter-exchange da fila.
 * - Logs de falha usam só eventId/correlationId: Message.toString() imprime o corpo JSON (com e-mail).
 */
@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Mesmos argumentos da declaração na familhas-api; divergência gera PRECONDITION_FAILED. */
    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(MessagingTopology.EVENTS_EXCHANGE)
                .durable(true)
                .alternate(MessagingTopology.UNROUTED_EXCHANGE)
                .build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(MessagingTopology.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue userRegisteredQueue() {
        return QueueBuilder.durable(MessagingTopology.USER_REGISTERED_QUEUE)
                .deadLetterExchange(MessagingTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MessagingTopology.USER_REGISTERED_QUEUE)
                .build();
    }

    @Bean
    Queue userRegisteredDeadLetterQueue() {
        return QueueBuilder.durable(MessagingTopology.USER_REGISTERED_DLQ).build();
    }

    @Bean
    Binding userRegisteredBinding() {
        return BindingBuilder.bind(userRegisteredQueue()).to(eventsExchange())
                .with(MessagingTopology.USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    Binding userRegisteredDeadLetterBinding() {
        return BindingBuilder.bind(userRegisteredDeadLetterQueue()).to(deadLetterExchange())
                .with(MessagingTopology.USER_REGISTERED_QUEUE);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            EmailProperties properties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryAdvice(properties.retry()));
        factory.setErrorHandler(new SafeLoggingErrorHandler());
        return factory;
    }

    static Advice retryAdvice(EmailProperties.Retry retry) {
        // traverseCauses: o listener lança ListenerExecutionFailedException com a exceção real como causa.
        SimpleRetryPolicy policy = new SimpleRetryPolicy(retry.maxAttempts(),
                Map.of(TransientProcessingException.class, true), true, false);
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(policy)
                .backOffPolicy(backOff)
                .recoverer(deadLetterRecoverer())
                .build();
    }

    /** Fim da linha: registra o motivo sem dados pessoais e rejeita sem requeue (vai para a DLQ). */
    static MessageRecoverer deadLetterRecoverer() {
        return (message, cause) -> {
            Throwable reason = unwrap(cause);
            log.error("Mensagem enviada para a DLQ: {} motivo={}", EventLogContext.describe(message),
                    safeReason(reason));
            throw new AmqpRejectAndDontRequeueException("Enviada para a DLQ");
        };
    }

    /**
     * Mensagem só das exceções da própria aplicação, que nunca carregam dado pessoal.
     * De qualquer outra exceção (driver, framework) loga só o tipo.
     */
    static String safeReason(Throwable reason) {
        boolean own = reason instanceof br.com.familhas.messaging.InvalidEventException
                || reason instanceof TransientProcessingException
                || reason instanceof br.com.familhas.email.PermanentEmailException;
        return own ? reason.getClass().getSimpleName() + ": " + reason.getMessage()
                : reason.getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable cause) {
        Throwable current = cause;
        while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * O handler padrão loga a exceção com stack trace e, em alguns casos, a mensagem inteira.
     * Este loga só o resumo seguro e mantém a decisão de rejeitar sem requeue.
     */
    static class SafeLoggingErrorHandler extends ConditionalRejectingErrorHandler {

        @Override
        protected void log(Throwable t) {
            Throwable reason = unwrap(t);
            if (reason instanceof AmqpRejectAndDontRequeueException) {
                // O recoverer já registrou o envio para a DLQ, com eventId e motivo.
                log.debug("Mensagem rejeitada sem requeue (DLQ)");
                return;
            }
            String described = t instanceof ListenerExecutionFailedException failed
                    ? EventLogContext.describe(failed.getFailedMessage())
                    : "eventId=? correlationId=?";
            log.warn("Falha no consumidor: {} motivo={}", described, reason.getClass().getSimpleName());
        }
    }
}
