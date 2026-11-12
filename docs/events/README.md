# Contratos de eventos

Eventos de integração entre a `familhas-api` (produtora) e os consumidores, como o `familhas-email`. Decisões em [ADR 0005](../adr/0005-mensageria-outbox-e-familhas-email.md).

O JSON Schema de cada evento é a fonte da verdade. Os dois serviços têm as próprias classes, sem jar compartilhado, e cada um tem um teste de contrato que valida contra o schema deste diretório.

## Catálogo

| Evento | Versão | Routing key | Schema | Produtor | Consumidores |
|---|---|---|---|---|---|
| `UserRegistered` | 1 | `client.user-registered.v1` | [user-registered.v1.schema.json](user-registered.v1.schema.json) | familhas-api (primeiro login) | familhas-email (boas-vindas) |
| `BookingCreated` | 1 | `booking.booking-created.v1` | [booking-created.v1.schema.json](booking-created.v1.schema.json) | familhas-api (reserva criada) | familhas-email (reserva aguardando pagamento) |
| `PaymentConfirmed` | 1 | `payment.payment-confirmed.v1` | [payment-confirmed.v1.schema.json](payment-confirmed.v1.schema.json) | familhas-api (webhook ou reconciliação) | familhas-email (reserva confirmada); Admin na Fase 10 |
| `PaymentFailed` | 1 | `payment.payment-failed.v1` | [payment-failed.v1.schema.json](payment-failed.v1.schema.json) | familhas-api (webhook ou reconciliação) | familhas-email (pagamento não aprovado); Admin na Fase 10 |

Decisões dos eventos de reserva e pagamento em [ADR 0007](../adr/0007-reserva-checkout-e-webhooks-stripe.md) (D10). `PaymentConfirmed` já implica reserva confirmada; um `BookingConfirmed` separado fica para a Fase 10, se o Admin precisar.

Valores monetários vão em texto com 2 casas (`"7830.16"`) e moeda ISO 4217 em campo próprio: nenhum consumidor perde precisão convertendo para ponto flutuante.

## Corpo da mensagem

O corpo é o JSON do evento, em UTF-8 e sem envelope. Todo evento tem estes campos-base:

| Campo | Tipo | Regra |
|---|---|---|
| `eventId` | UUID | Único por evento. Chave de idempotência de todo consumidor. |
| `occurredAt` | ISO-8601 UTC | Quando o fato aconteceu no domínio, não quando foi publicado. |
| `schemaVersion` | inteiro ≥ 1 | Versão do contrato do evento. |

Os demais campos são específicos de cada evento. Exemplo: [examples/user-registered.v1.json](examples/user-registered.v1.json).

## Propriedades AMQP

| Propriedade | Valor |
|---|---|
| `message_id` | `eventId` |
| `type` | nome do evento, por exemplo `UserRegistered` |
| `correlation_id` | correlationId da requisição de origem, ou o próprio `eventId` quando não houver um |
| `content_type` / `content_encoding` | `application/json` / `UTF-8` |
| `delivery_mode` | persistente |
| `timestamp` | instante da publicação |
| header `x-schema-version` | igual ao `schemaVersion` do corpo |

## Topologia no RabbitMQ

```
familhas-api ──► familhas.events (topic, durável, alternate-exchange=familhas.events.unrouted)
                   │  client.user-registered.v1
                   ▼
                 familhas-email.user-registered ──(rejeitada)──► familhas.events.dlx (direct)
                                                                  │  familhas-email.user-registered
                                                                  ▼
                                                                familhas-email.user-registered.dlq

familhas.events.unrouted (fanout) ──► familhas.events.unrouted (fila): mensagens sem nenhuma fila ligada
```

- O exchange `familhas.events` é declarado pelos dois serviços com os mesmos argumentos. Se os argumentos divergirem, o RabbitMQ recusa a declaração (`PRECONDITION_FAILED`).
- A fila do consumidor, o DLX e a DLQ são do consumidor. A convenção de nomes é `<servico>.<evento>` e `<servico>.<evento>.dlq`.
- Mensagens publicadas antes de qualquer consumidor existir vão para `familhas.events.unrouted` e não se perdem.

## Regras de versionamento

- **Mudança compatível** (novo campo opcional, novo valor aceito): o `schemaVersion` sobe e a routing key continua `.v1`. Consumidores ignoram campos desconhecidos.
- **Mudança incompatível** (remover ou renomear campo, mudar tipo ou semântica): novo arquivo `*.v2.schema.json` e nova routing key `.v2`. A API publica as duas versões até todos os consumidores migrarem.
- Dentro da mesma routing key toda versão é compatível por definição, então o consumidor aceita qualquer `schemaVersion` ≥ 1. Um valor ausente ou menor que 1 é payload inválido e vai para a DLQ.

## LGPD

`email` e `firstName` são dados pessoais (em todos os eventos acima). Os eventos de pagamento nunca levam dados de cartão: só método, valor e o código de recusa do Stripe. Eles vão no corpo porque o consumidor precisa deles para enviar o e-mail, mas nunca entram em log, header AMQP ou mensagem de erro. Os logs usam só `eventId` e `correlationId`.
