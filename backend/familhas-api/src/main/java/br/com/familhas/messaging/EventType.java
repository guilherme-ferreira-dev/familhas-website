package br.com.familhas.messaging;

/**
 * Metadados fixos de um tipo de evento de integração (docs/events/README.md).
 *
 * @param name          nome do evento, vai na propriedade AMQP {@code type} (ex.: UserRegistered)
 * @param routingKey    routing key no exchange familhas.events, com a versão maior no sufixo (ex.: .v1)
 * @param aggregateType agregado de origem, usado só no outbox para rastrear o evento
 */
public record EventType(String name, String routingKey, String aggregateType) {
}
