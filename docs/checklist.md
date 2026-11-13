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

## Fase 5: Catálogo, busca e cálculo de pacote

Decisões em [ADR 0006](adr/0006-provedores-preco-e-cotacao.md).

### Voo e seed
- [x] V9: origem, destino, preço por passageiro com moeda, assentos e status no voo
- [x] Seed com datas futuras (2027) e voo Brasília -> Maceió
- [x] CRUD admin e catálogo público de voos atualizados (só ativos na Loja)

### Provedores
- [x] Interfaces `FlightProvider`, `HotelProvider` e `CarRentalProvider`
- [x] Adapters do catálogo curado e mock (desligado por padrão)
- [x] Cache (Caffeine), timeout e circuit breaker (Resilience4j) por provedor, com resultado parcial
- [ ] Adapter `HotelDoHotelProvider`: desenho no ADR 0006; depende de contrato, credenciais e documentação da HotelDO

### Busca
- [x] `/api/search/flights`, `/hotels`, `/cars` e `/packages` com origem, destino, datas, passageiros, faixa de preço e categoria
- [x] Validação de datas (fuso de São Paulo), limites e Problem Details
- [x] Ordenação e paginação com lista branca

### Preço e cotação
- [x] `PricingService`: soma dos componentes, margem configurável, promoção e taxa por passageiro
- [x] Regras cobertas por testes parametrizados
- [x] V10: `quotes` e `quote_items` com snapshot e validade
- [x] `POST/GET /api/me/quotes` por pacote ou itens avulsos, só do próprio cliente
- [x] Margem e custo por item não aparecem para o cliente

### Testes e validação
- [x] Unitários nos services, WebMvcTest nos controllers e Testcontainers (Oracle) em busca e cotação
- [x] `./mvnw verify` passa em `backend/familhas-api`
- [x] Migração V9 e V10 validada sobre o banco local existente (V8) e fluxo busca -> cotação validado com token real

### Pendências documentadas (fora da Fase 5)
- [ ] Margem e taxas reais definidas pelo Enéas (hoje 10% e R$ 0,00)
- [ ] Alinhar o preço de vitrine dos pacotes com os componentes (Admin, Fase 10)
- [ ] Provedores reais de voos (Duffel ou Amadeus) e carros (Rentcars)
- [ ] Disponibilidade por data no catálogo curado de hotéis e carros
- [x] Reserva de assento e reverificação de disponibilidade (feito na Fase 6)
- [ ] Retenção de cotações expiradas (Fase 11)

## Fase 6: Reserva, checkout e pagamento (Stripe)

Decisões em [ADR 0007](adr/0007-reserva-checkout-e-webhooks-stripe.md) e [ADR 0008](adr/0008-parcelamento-no-cartao.md). Contrato HTTP em [docs/api/checkout.yaml](api/checkout.yaml).

### Contrato e decisões
- [x] OpenAPI de cotação, reserva, checkout e webhook em `docs/api/checkout.yaml`, publicado antes do código para a Fase 7 e validado por teste
- [x] ADR 0007: Stripe Checkout hospedado, bloqueio de assentos, máquinas de estado, webhooks, reconciliação e eventos
- [x] ADR 0008: parcelamento adiado até validar a conta Stripe Brasil

### Reserva
- [x] V11: `bookings` e `booking_items` com snapshot da cotação; cotação passa a `BOOKED`
- [x] `POST/GET /api/me/bookings`: só cotação `OPEN`, em BRL e do próprio cliente; uma reserva por cotação
- [x] Ofertas reconsultadas e assentos do voo curado bloqueados (update condicional) por 30 min
- [x] Máquina de estados `PENDING_PAYMENT → CONFIRMED → COMPLETED` / `CANCELLED`
- [x] Erros de negócio com `code` estável no Problem Details

### Pagamento
- [x] V12: `payments`, `installments` (sempre 1) e `stripe_webhook_events`
- [x] `stripe-java` atrás de `PaymentGateway`; moeda BRL; valores em centavos sem arredondamento
- [x] Stripe Checkout hospedado para cartão e Pix; dados de cartão nunca passam pela API
- [x] Idempotency key em toda escrita no Stripe; checkout idempotente pelo domínio (200 reaproveita, 201 cria)
- [x] Chaves só por variável de ambiente e só de teste; sem chave, checkout 503
- [x] Máquina de estados do pagamento, incluindo nova tentativa na mesma sessão e pagamento tardio de sessão substituída
- [ ] Parcelamento no cartão: depende da validação na conta Stripe Brasil (ADR 0008)

### Webhooks e reconciliação
- [x] `POST /api/webhooks/stripe` público, validado pela `Stripe-Signature` sobre o corpo cru, com tolerância contra replay
- [x] Idempotência por `event.id` na mesma transação do efeito
- [x] `checkout.session.*`, `payment_intent.processing/succeeded/payment_failed` e `charge.refunded`
- [x] Estorno total cancela a reserva e devolve os assentos; parcial mantém `CONFIRMED`
- [x] Estorno automático de pagamento aprovado com reserva cancelada ou já paga
- [x] Reconciliação agendada: pagamentos parados e reservas vencidas

### Eventos e e-mails
- [x] `BookingCreated`, `PaymentConfirmed` e `PaymentFailed` pelo outbox, com JSON Schema e exemplos em `docs/events`
- [x] `familhas-email`: um e-mail por evento, fila e DLQ próprias, idempotência por `eventId`
- [x] Testes de contrato nos dois serviços

### Testes e validação
- [x] Unitários nos services, WebMvcTest nos controllers e webhooks assinados em teste
- [x] Testcontainers: Oracle (reserva e fluxo completo), stripe-mock (adapter e fluxo), RabbitMQ + MailHog (e-mails)
- [x] `./mvnw verify` passa em `backend/familhas-api` e `backend/familhas-email`
- [ ] Validação manual com conta Stripe de teste (`stripe listen`), cartão `4242...` e Pix

### Pendências documentadas (fora da Fase 6)
- [ ] Conta Stripe Brasil: Pix habilitado, parcelamento, taxas e verificação da empresa (guia, seção 11)
- [ ] Estorno pelo Admin e planilha de pagamentos alimentada pelos eventos (Fase 10)
- [ ] `COMPLETED` quando a viagem termina e "Minhas viagens" (Fase 8)
- [ ] Estoque por data de hotel e carro no catálogo curado (pendência da Fase 5)
- [ ] Retenção de `stripe_webhook_events` e de reservas canceladas (Fase 11)
- [ ] Chave live e conta em produção (novo ADR)

## Fase 7: Frontend da Loja

Decisões em [ADR 0009](adr/0009-frontend-da-loja.md). Rodou em paralelo à Fase 6.

### Base
- [x] `frontend/familhas-web` com Angular 22 (standalone, zoneless), ESLint (com regras de acessibilidade), Prettier e Vitest
- [x] Configuração de runtime por ambiente (`config.json` validado): API, Keycloak e WhatsApp
- [x] Contrato `docs/api/familhas-api.yaml` (catálogo, busca, `/api/me`) e tipos gerados dele e do `checkout.yaml` da Fase 6
- [x] Mocks MSW seguindo os contratos, só no build `mock`

### Autenticação e erros
- [x] Login e logout com OIDC + PKCE no Keycloak (client `familhas-web`), refresh token sem iframe
- [x] Interceptor JWT só para `{apiBaseUrl}/api/`; guard nas rotas do cliente com volta para a rota pedida (sem open redirect)
- [x] CORS restrito: a API já libera só as origens da Loja (`CORS_ALLOWED_ORIGINS`, Fase 3); nada a mudar no backend
- [x] Problem Details vira mensagem em português pelo `code`; 401 leva ao login; avisos em `aria-live`; página 404

### Telas
- [x] Home com busca rápida, ofertas e categorias
- [x] Busca de pacotes, voos, hotéis e carros com filtros na URL, validação, ordenação, paginação e aviso de resultado parcial
- [x] Detalhe do pacote (voo, hospedagem, carro) e cotação com total, taxas e validade
- [x] Checkout: reserva, Pix ou cartão, Stripe Checkout hospedado e polling da confirmação
- [x] Botão flutuante de WhatsApp em todas as telas, `wa.me` com contexto (pacote, cotação ou reserva) e número por ambiente

### Qualidade
- [x] Mobile-first (375 px sem rolagem horizontal), SEO básico (título, description, canonical, Open Graph, robots)
- [x] Acessibilidade: pular para o conteúdo, foco na navegação, landmarks, erros de campo, contraste AA; axe sem violações no fluxo principal
- [x] Testes: unitários (config, auth, interceptors, erros, WhatsApp, busca, polling, redirect) e de tela com MSW
- [x] Job `familhas-web` no CI: contratos, tipos, lint, formatação, testes e builds

### Pendências documentadas (fora da Fase 7)
- [ ] Número oficial do WhatsApp Business do Enéas no `config.json` de cada ambiente (hoje placeholder `5500000000000`)
- [ ] Domínios dos ambientes: `siteUrl`, redirect URIs do Keycloak e `CORS_ALLOWED_ORIGINS`
- [ ] Validar login, checkout e webhook de ponta a ponta com o backend real (Keycloak e conta Stripe de teste)
- [ ] Cotação de itens avulsos (voo, hotel ou carro) direto da busca
- [ ] Minhas viagens e recomendação (Fase 8); chat (Fase 9)
- [ ] SSR ou prerender para SEO, quando houver hospedagem definida
- [ ] Imagens reais dos pacotes e categorias (hoje gradiente quando `imageUrl` é nulo)

## Fase 8: Minhas viagens e recomendação

Decisões em [ADR 0010](adr/0010-minhas-viagens-e-recomendacao.md). Contrato em [docs/api/minhas-viagens.yaml](api/minhas-viagens.yaml).

### Decisões
- [x] Trip derivada de `Booking` `CONFIRMED`/`COMPLETED`, sem tabela própria; id da viagem = id da reserva
- [x] Futura x passada pela data do último item (America/Sao_Paulo) ou `COMPLETED`, sem job
- [x] `client_flights`/`myFlights` mantido como legado e marcado `deprecated` no contrato
- [x] Migration V13 só com o índice `bookings (client_id, status)` (numeração combinada com a Fase 10: V13/ADR 0010 x V14+/ADR 0011)

### API (familhas-api)
- [x] `GET /api/me/trips?when=UPCOMING|PAST` e `GET /api/me/trips/{id}` com itens, total, pagamento, parcelas e avaliação do cliente
- [x] `GET`, `PUT` e `DELETE /api/me/favorites[/{packageId}]`, idempotentes; só pacote ativo
- [x] `/api/me/ratings`: avaliação só de pacote de viagem passada; resposta com pacote resumido; erros com `code`
- [x] `RecommendationService` (interface estável) + `RuleBasedRecommendationService` + `RecommendationRules` (função pura)
- [x] `GET /api/me/recommendations?limit=` (1 a 20) com `reasons`
- [x] DTOs em todas as respostas; erros em Problem Details com `code` (`TRIP_NOT_FOUND`, `PACKAGE_NOT_FOUND`, `RATING_*`)
- [x] v2 (ML/embeddings) só documentada no ADR 0010

### Loja (familhas-web)
- [x] Página `/minhas-viagens` (authGuard, noindex): abas próximas/realizadas na URL, pagamento e parcelas, avaliação, favoritos, avaliações e sugestões
- [x] Coração de favoritar no cartão e no detalhe do pacote (sem sessão leva ao login); link no cabeçalho e no fim do pagamento
- [x] Tipos gerados do novo contrato; handlers MSW com as mesmas regras da API

### Testes e validação
- [x] Unitários e WebMvcTest: classificação por data, paginação, pagamento exibido, 401/403/404, favoritos, regras de avaliação, recomendação parametrizada
- [x] Teste de contrato do `minhas-viagens.yaml`
- [x] `MinhasViagensIntegrationTests` (Oracle, HTTP com JWT, isolamento entre clientes): 4 testes passando
- [x] Loja: testes de tela com MSW (página, favoritos, detalhe do pacote), lint, formatação e build

### Pendências documentadas (fora da Fase 8)
- [ ] Marcar `COMPLETED` (job ou Admin) e remover `client_flights`: decisão de fase futura
- [ ] Afinidade de destino além do país (região/tags em `CityEntity`) e pesos da recomendação validados com o Enéas
- [ ] Parcelamento real no cartão continua adiado (ADR 0008); a tela já mostra a lista de parcelas quando houver mais de uma

