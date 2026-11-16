# Checklist E2E local

Roteiro manual para validar a plataforma inteira na máquina local, de ponta a ponta: infraestrutura, Loja, pagamento, e-mail, Admin e operação. Cobre os critérios de aceite globais do guia (seção 10). Leva uns 30 minutos.

Copie a lista para o PR ou issue e marque cada item. Anote o `X-Correlation-Id` de qualquer falha (aparece na resposta da API e no DevTools > Network).

## 0. Preparação

- [ ] Docker Desktop com pelo menos 4 GB de memória ([infra/README.md](../infra/README.md)).
- [ ] `cp .env.example .env` (se ainda não existe). Para pagamento real em teste: `STRIPE_SECRET_KEY=sk_test_...` exportada no shell da API.
- [ ] Stripe CLI logado (`stripe login`), só para a seção 4.

## 1. Infraestrutura

- [ ] `docker compose up -d --wait` termina com todos `healthy`: `docker compose ps`.
- [ ] RabbitMQ em http://localhost:15672 (guest/guest): filas `familhas-email.*` e `*.dlq` existem.
- [ ] MailHog em http://localhost:8025.
- [ ] Keycloak em https://localhost:8443 (aceite o certificado self-signed).
- [ ] `familhas-email`: `curl -s localhost:8081/actuator/health` = `{"status":"UP"}`.

## 2. API

- [ ] `cd backend/familhas-api && ./mvnw spring-boot:run` sobe; o log mostra o Flyway validando até a última migration.
- [ ] `curl -s localhost:8080/actuator/health` = `{"status":"UP"}` (sem detalhes, sem token).
- [ ] `curl -sI localhost:8080/api/categories` tem `X-Correlation-Id`, `Content-Security-Policy`, `X-Frame-Options: DENY` e `Referrer-Policy`.
- [ ] Swagger UI em http://localhost:8080/swagger-ui.html, com os grupos **public** e **admin**; o grupo public não tem `/api/admin`.
- [ ] **Critério global:** `curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/me` = `401`.
- [ ] **Critério global:** com token de cliente (Postman, pasta Cliente), `GET /api/admin/dashboard` = `403`; com token de admin = `200`.
- [ ] `curl -s -o /dev/null -w '%{http_code}' localhost:8080/actuator/prometheus` = `401`; com token de admin, mostra `familhas_outbox_events`.

## 3. Loja (familhas-web)

`cd frontend/familhas-web && npm ci && npm start` → http://localhost:4200

- [ ] Home carrega ofertas e categorias, sem erro no console.
- [ ] Busca com origem, destino e datas; filtros ficam na URL; paginação funciona.
- [ ] Detalhe do pacote mostra voo, hospedagem e carro.
- [ ] Botão do WhatsApp aparece em todas as telas e abre `wa.me` com o contexto da página.
- [ ] Entrar com `user.familhas` / `user12345` volta para a página de onde saiu.
- [ ] Primeiro login de um usuário novo (crie em "Registrar"): e-mail de boas-vindas chega no MailHog uma vez só; um segundo login não manda outro.
- [ ] Cotação do pacote mostra total, taxas e validade; nenhum custo ou margem aparece.
- [ ] Favoritar um pacote (coração) e ver em **Minhas viagens > Favoritos**.
- [ ] Em 375 px de largura não há rolagem horizontal.

## 4. Pagamento (Stripe de teste)

Precisa de `STRIPE_SECRET_KEY` de teste na API e do webhook local:

```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
# copie o whsec_... para STRIPE_WEBHOOK_SECRET e reinicie a API
```

- [ ] Reservar a cotação: reserva `PENDING_PAYMENT` e e-mail "reserva criada" no MailHog.
- [ ] Pagar com cartão `4242 4242 4242 4242` (qualquer data futura e CVC): a Loja mostra a confirmação.
- [ ] **Critério global:** reserva vira `CONFIRMED`, e-mail de pagamento confirmado no MailHog e linha atualizada na planilha do Admin (seção 5) sem recarregar a página.
- [ ] **Critério global, sem duplicar:** `stripe events resend <evt_id>` do `payment_intent.succeeded` responde 200 e não gera segundo e-mail nem segunda linha.
- [ ] Cartão recusado `4000 0000 0000 0002`: a Loja mostra a recusa e o e-mail de falha chega.
- [ ] Pix (se habilitado na conta): o Checkout mostra o QR code.
- [ ] Viagem aparece em **Minhas viagens > Próximas** com status de pagamento.

## 5. Admin (familhas-admin)

`cd frontend/familhas-admin && npm ci && npm start` → http://localhost:4300

- [ ] Login com `user.familhas` é negado ("sem permissão").
- [ ] Login com `admin.familhas` / `admin1234` abre o dashboard com os números do período.
- [ ] Clientes: abas novos, ativos e passados; o cliente da seção 3 aparece em "novos".
- [ ] Planilha de pagamentos: filtro por período, status e método; exportar CSV e XLSX abre com os filtros aplicados.
- [ ] Cotação manual para um cliente com desconto; o cliente vê a cotação na Loja, sem a nota interna.
- [ ] Alterar a margem; a próxima cotação usa a margem nova.
- [ ] Auditoria lista a cotação manual, a exportação e a margem, com quem fez.

## 6. Observabilidade e operação

- [ ] Uma requisição da Loja que gera evento (cotação, reserva): o mesmo `X-Correlation-Id` aparece no log da API, na linha do outbox e no log do `familhas-email` (`docker compose logs familhas-email | grep <id>`).
- [ ] Com `LOG_FORMAT=ecs` exportado, a API loga em JSON com o campo `correlationId`.
- [ ] Rate limit: `for i in $(seq 1 40); do curl -s -o /dev/null -w '%{http_code} ' -X PUT -H "Authorization: Bearer $TOKEN" localhost:8080/api/me/favorites/<packageId>; done` termina em `429`, com `Retry-After`.
- [ ] Mensagem inválida vai para a DLQ e aparece em `familhas_email_dlq_messages` (`curl -s localhost:8081/actuator/prometheus | grep dlq`). Para gerar: RabbitMQ > Exchanges > `familhas.events` > publish com routing key `client.user-registered.v1` e corpo `{}`.
- [ ] `infra/oracle/backup.sh` gera o dump e `infra/oracle/restore.sh --verify <dump>` termina sem erro ([backup-restore.md](operacao/backup-restore.md)).

## 7. Critérios de aceite globais (guia, seção 10)

- [ ] `docker compose up` sobe toda a infraestrutura (seção 1).
- [ ] `./mvnw verify` passa em `backend/familhas-api` e `backend/familhas-email`.
- [ ] Os frontends compilam e passam no lint: `npm run lint && npm run build` em `familhas-web` e `familhas-admin`.
- [ ] Sem token 401, sem permissão 403, admin acessa `/api/admin/**` e cliente não (seção 2).
- [ ] Webhook gera `CONFIRMED`, e-mail no MailHog e linha na grade do Admin, sem duplicar em reprocessamento (seção 4).
- [ ] Nenhum segredo ou dado de cartão no repositório ou nos logs: `git grep -nE "(sk|rk)_(live|test)_[A-Za-z0-9]{20,}|whsec_[A-Za-z0-9]{20,}"` vazio, e os logs da seção 6 só têm ids.
