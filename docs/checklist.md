# Checklist de implementação

Acompanha as fases do Guia Técnico de Implementação (versão 1.0, 27/10/2026, seção 8).

## Fases 0 a 3

- [x] Fase 0: auditoria, monorepo e CI (PR #14)
- [x] Fase 1: infraestrutura local com healthchecks (PR #15)
- [x] Fase 2: domínio, migrations, seed e paginação (PR #16)
- [x] Fase 3: segurança com Keycloak, JWT e Problem Details (PR #17)

## Fase 4: Mensageria e onboarding

Decisões em [ADR 0005](adr/0005-mensageria-outbox-e-familhas-email.md).

### Contrato
- [x] `UserRegisteredEvent` versionado com `eventId`, `userId`, `email`, `firstName`, `occurredAt` e `schemaVersion`
- [x] JSON Schema, exemplo e regras de versionamento em [docs/events](events/README.md)
- [x] Teste de contrato nos dois serviços contra o mesmo schema

### Producer (familhas-api)
- [x] Migration V8 com a tabela `outbox_event`
- [x] `OutboxService` grava na transação de quem chama (`MANDATORY`)
- [x] Publisher agendado com `SKIP LOCKED`, publisher confirms, backoff e `FAILED` depois do máximo de tentativas
- [x] Exchange topic `familhas.events` com alternate exchange para mensagens sem fila ligada
- [x] Evento publicado na criação do `Client` no primeiro login (`KeycloakClientSyncService`)
- [x] Testcontainers (Oracle + RabbitMQ): publicação, rollback, recusa fora de transação, mensagem sem fila, onboarding

### Consumidor (familhas-email)
- [x] Módulo `backend/familhas-email` (Spring Boot, build próprio, pacote `br.com.familhas`)
- [x] Validação do payload (Bean Validation) com DLQ para payload inválido
- [x] Idempotência por `eventId` na tabela `processed_event` (schema `FAMILHAS_EMAIL`)
- [x] Envio SMTP para o MailHog
- [x] Retry com backoff para falha transitória e DLQ para falha permanente ou tentativas esgotadas
- [x] Logs só com `eventId` e `correlationId` (LGPD)
- [x] Testcontainers (Oracle + RabbitMQ + MailHog): envio, reprocessamento sem duplicar, DLQ, retry, falha permanente

### Base reutilizável
- [x] API: `IntegrationEvent`, `EventType`, `OutboxService`
- [x] Consumidor: `ConsumedEvent`, `EventMessageReader`, `IdempotentEventProcessor`, `EventLogContext`
- [ ] `BookingConfirmed` e `PaymentConfirmed`: só a base nesta fase; os eventos entram nas Fases 6 e 10

### Infra, CI e documentação
- [x] `familhas-email` no `docker compose`, com healthcheck
- [x] Usuário Oracle `FAMILHAS_EMAIL` criado no init, com senha por variável de ambiente
- [x] `.env.example` completo e sem valores reais
- [x] Job do `familhas-email` no CI (verify e build da imagem)
- [x] ADR 0005, README, infra/README e este checklist

### Critérios de aceite
- [x] `docker compose up` sobe toda a infraestrutura, incluindo o `familhas-email`
- [x] Primeiro login cria o cliente, o evento sai pelo outbox e o e-mail de boas-vindas aparece no MailHog (validado com Keycloak real; segundo login não gera outro e-mail)
- [x] Reprocessar o mesmo `eventId` não envia o e-mail de novo
- [x] Payload inválido ou falha permanente vai para a DLQ
- [x] `./mvnw verify` passa em `backend/familhas-api` e `backend/familhas-email`

### Pendências documentadas (fora da Fase 4)
- [ ] Filtro de `correlationId` nas requisições HTTP (Fase 11)
- [ ] Retenção e limpeza de `outbox_event` e `processed_event` (Fase 11)
- [ ] Métricas de outbox e DLQ no Actuator (Fase 11)
- [ ] Tela ou endpoint admin para reprocessar outbox `FAILED` e DLQ (Fase 10)
- [ ] Listener de eventos do Keycloak via SPI (evolução, se necessário)
- [ ] Confiança da API no certificado self-signed do Keycloak local (truststore). Hoje há só o contorno manual no infra/README; lacuna herdada da Fase 3
