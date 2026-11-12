# ADR 0005: Mensageria com outbox, contrato de eventos e familhas-email

- Status: aceito
- Data: 11/11/2026
- Fase: 4

## Contexto

O guia (seção 6, item 6, e Fase 4) pede eventos no RabbitMQ com Transactional Outbox, exchanges do tipo topic, DLQ por fila e consumidores idempotentes por `eventId`. O primeiro evento é o `UserRegisteredEvent`, que dispara o e-mail de boas-vindas pelo novo serviço `familhas-email`.

Antes desta fase, a `familhas-api` tinha propriedades de RabbitMQ e SMTP, mas nenhuma dependência de AMQP ou de e-mail, e nenhum producer ou consumer. O `.env.example` citado no README não existia.

Na primeira resposta da fase, cinco perguntas ficaram em aberto. O responsável aprovou os defaults propostos, e eles estão registrados abaixo como decisões D1 a D5.

## Decisões

**D1. Onde ficam as tabelas**
- `outbox_event` fica no schema da API (migration V8). Isso não era opcional: o evento precisa ser gravado na mesma transação do `Client`.
- `processed_event` fica no schema Oracle próprio do `familhas-email` (`FAMILHAS_EMAIL`), com Flyway próprio (V1). Cada serviço é dono dos seus dados, e o `familhas-email` não tem acesso às tabelas da API.
- O usuário `FAMILHAS_EMAIL` é criado por `infra/oracle/init/02-create-familhas-email-user.sh`, com privilégio mínimo (sessão, criar tabela, cota) e senha vinda de variável de ambiente.

**D2. Quando o evento sai**
- Só quando o `KeycloakClientSyncService` cria o `Client` no primeiro login.
- Vincular por e-mail um cliente que o Enéas já tinha cadastrado não gera evento, porque não é um cadastro novo. Logins seguintes também não geram.
- Não há listener de eventos do Keycloak (SPI). Ele fica como evolução: só seria necessário se o e-mail tivesse de sair antes do primeiro acesso à API.

**D3. Base de eventos sem jar compartilhado**
- O JSON Schema em `docs/events/*.schema.json` é a fonte da verdade do contrato. Cada serviço tem as próprias classes:
  - na API, `IntegrationEvent` e `EventType`;
  - no `familhas-email`, `ConsumedEvent`, `EventMessageReader` e `IdempotentEventProcessor`.
- Os dois módulos têm um teste de contrato que valida contra o mesmo schema e o mesmo exemplo.
- Isso mantém os builds independentes do ADR 0001, sem POM agregador.
- Para adicionar `BookingConfirmed` ou `PaymentConfirmed`: novo schema, um record que implementa `IntegrationEvent` na API, e fila, listener e `ConsumedEvent` no consumidor.

**D4. O que é `userId`**
- É o UUID do `Client` local. O `sub` do Keycloak não sai da API.

**D5. Routing key e versionamento**
- A routing key é `<agregado>.<evento>.v<maior>`, por exemplo `client.user-registered.v1`.
- Mudança compatível: aumenta `schemaVersion` e mantém a routing key.
- Mudança incompatível: novo schema e routing key `.v2`.
- Regras completas em [docs/events/README.md](../events/README.md).

**Producer (familhas-api)**
- `OutboxService.append` exige transação ativa (`MANDATORY`). O evento e a mudança de domínio são gravados juntos ou não são gravados.
- O `OutboxPublisher` roda a cada 1 s:
  1. trava um lote com `FOR UPDATE SKIP LOCKED`, que deixa várias instâncias da API publicarem sem conflito;
  2. publica com publisher confirms e marca `PUBLISHED`.
- Na primeira falha, o lote para. A linha que falhou ganha backoff exponencial (2 s, dobrando até 5 min). Depois de 20 tentativas, vira `FAILED`.
- O Oracle não aceita `FETCH FIRST` com `FOR UPDATE`, então o limite do lote usa `ROWNUM`. A ordem de publicação é aproximada.
- O exchange `familhas.events` (topic) tem o alternate exchange `familhas.events.unrouted`. Uma mensagem publicada antes de existir qualquer fila ligada fica guardada lá e não se perde.
- O `correlationId` vem do MDC (chave `correlationId`). Como o filtro que preenche essa chave só entra na Fase 11, hoje o valor é o próprio `eventId`.

**Consumidor (familhas-email)**
- Fila `familhas-email.user-registered`, com DLX `familhas.events.dlx` e DLQ `familhas-email.user-registered.dlq`. O consumidor declara a própria fila, a DLX e a DLQ.
- Validação com Bean Validation, espelhando o schema. Campos desconhecidos são ignorados.
- Idempotência: a linha `(event_id, consumer)` entra em `processed_event` antes do envio, na mesma transação.
  - Se o envio falha, o rollback libera o `eventId`.
  - Se a mesma mensagem chega de novo, a chave primária impede o segundo envio.
- Classificação de falhas:
  - **Transitória:** conexão recusada, timeout, SMTP 4xx. Até 5 tentativas com backoff exponencial (1 s, 2 s, 4 s, até 10 s). Esgotadas as tentativas, vai para a DLQ.
  - **Permanente:** SMTP 5xx, endereço inválido, autenticação ou payload inválido. Vai direto para a DLQ, sem retry.
- Concorrência 1 e prefetch 1: e-mail transacional tem volume baixo, e o retry segura a thread durante o backoff.
- O serviço entra no `docker compose` como `familhas-email`, com healthcheck em `/actuator/health`. A API continua rodando fora do compose (`./mvnw spring-boot:run`).

**LGPD**
- Os logs dos dois lados usam só `eventId`, `correlationId`, tipo e motivo. O padrão de log do `familhas-email` inclui o MDC.
- O `Message.toString()` do Spring AMQP imprime o corpo JSON, que contém o e-mail. Por isso o `familhas-email` usa recoverer e error handler próprios, que nunca logam a mensagem.
- As exceções de SMTP não encadeiam a causa original, porque as respostas SMTP costumam citar o endereço do destinatário.
- Os records de evento sobrescrevem `toString` sem e-mail nem nome.
- Um teste de integração captura a saída do log e verifica que o e-mail e o nome não aparecem.

## Consequências

- **Entrega pelo menos uma vez:**
  - Se o commit do outbox falhar depois do confirm, a mensagem é publicada de novo. O consumidor idempotente absorve.
  - Se o SMTP aceitar a mensagem e o commit do `processed_event` falhar em seguida, o e-mail pode sair duas vezes. Essa janela é pequena e foi aceita.
- **Reprocessamento manual** (sem tela nesta fase):
  - Outbox `FAILED`: `UPDATE outbox_event SET status = 'PENDING', attempts = 0, available_at = SYSTIMESTAMP WHERE id = ...` no schema da API.
  - DLQ: corrigir a causa e mover as mensagens de volta pelo painel do RabbitMQ (Queues > DLQ > Move messages), ou por shovel. Como o rollback libera o `eventId`, a mensagem reprocessada envia normalmente.
- **Volume do Oracle já existente:** os scripts de `infra/oracle/init` só rodam na criação do volume. Quem já tinha o volume precisa recriá-lo (`docker compose down -v`) ou rodar o script à mão (passo a passo no `infra/README.md`).
- **Argumentos do exchange duplicados:** o `familhas.events` é declarado pelos dois serviços com os mesmos argumentos. Se um lado mudar e o outro não, o RabbitMQ recusa a declaração (`PRECONDITION_FAILED`). Qualquer mudança no exchange precisa ser feita nos dois serviços ao mesmo tempo.
- **Sem limpeza automática:** `outbox_event` (linhas `PUBLISHED`) e `processed_event` crescem sem limpeza. Retenção e job de limpeza ficam para a Fase 11. Os dois têm índice por data para isso.
- **Retry bloqueante:** o retry segura o consumidor por até ~15 s por mensagem. Com volume maior, a evolução é usar filas de espera com TTL (delayed retry).
- **Evoluções só documentadas:** evento do Keycloak via SPI, filtro de `correlationId` (Fase 11), métricas de outbox e DLQ (Fase 11), e template HTML para os e-mails.
