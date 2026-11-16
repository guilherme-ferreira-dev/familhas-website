# Familhas

Plataforma da agência de viagens Familhas: **Loja** (busca, cotação, compra e "Minhas viagens") e **Admin** (gestão de clientes, reservas e pagamentos).

## Estrutura

```
backend/familhas-api     API Spring Boot (Java 21), monólito modular, produtora de eventos (outbox)
backend/familhas-email   Consumidor de eventos que envia e-mails transacionais (boas-vindas)
frontend/familhas-web    Loja em Angular 22 (busca, pacote, cotação, checkout e WhatsApp)
frontend/familhas-admin  CRM interno Voe Familhas em Angular 22 (ADR 0013/0014); telas do Admin da Loja só para crm-admin
infra/                   Docker Compose: Oracle, RabbitMQ, Keycloak, MailHog e familhas-email
docs/adr/                Registro de decisões de arquitetura
docs/events/             Contratos versionados dos eventos (JSON Schema)
docs/api/                Contratos HTTP (OpenAPI) consumidos pelos frontends
docs/postman/            Coleção Postman da API
docs/seguranca/          Revisão de segurança (OWASP Top 10)
docs/operacao/           Backup e restore do Oracle
docs/e2e-checklist.md    Checklist E2E local
docs/checklist.md        Checklist das fases
docs/requirements-matrix.md  Matriz JSON do CRM interno -> código -> lacuna -> ação
.github/workflows/       CI (build e testes de cada aplicação)
```

## Pré-requisitos

- Java 21
- Docker Desktop (infraestrutura local e testes de integração com Oracle e stripe-mock)
- [Stripe CLI](https://docs.stripe.com/stripe-cli), só para testar pagamentos com uma conta Stripe de teste (opcional)
- Node 24 LTS (só para `frontend/familhas-web` e `frontend/familhas-admin`)

Não é preciso instalar Maven: use o wrapper (`mvnw` / `mvnw.cmd`).

## Executando localmente (passo a passo)

Do zero até Loja e Admin funcionando. Depois de subir, valide com a [checklist E2E](docs/e2e-checklist.md).

### 1. Configuração

```bash
git clone https://github.com/guilherme-ferreira-dev/familhas-website.git
cd familhas-website
cp .env.example .env
```

O `.env` é ignorado pelo Git e nunca deve ser commitado. Variável vazia usa o padrão local. O Docker Desktop precisa de pelo menos 4 GB de memória ([infra/README.md](infra/README.md)).

### 2. Infraestrutura

```bash
docker compose up -d --wait
docker compose ps          # todos healthy
```

Sobe Oracle, RabbitMQ, Keycloak, MailHog e o `familhas-email`. O `--wait` só retorna com tudo `healthy`; na primeira vez o Oracle leva alguns minutos e a imagem do `familhas-email` é construída a partir do código. Depois de mudar o código do `familhas-email`: `docker compose up -d --build familhas-email`.

| Serviço | Endereço | Acesso |
|---|---|---|
| Keycloak | https://localhost:8443 | `admin` / `admin` (aceite o certificado self-signed) |
| RabbitMQ | http://localhost:15672 | `guest` / `guest` |
| MailHog | http://localhost:8025 | e-mails enviados |
| familhas-email | http://localhost:8081/actuator/health | métricas em `/actuator/prometheus` |

### 3. API

```bash
cd backend/familhas-api
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

O Flyway aplica as migrations e o Hibernate valida o schema. Confira:

- `curl localhost:8080/actuator/health` → `{"status":"UP"}`
- Swagger UI: http://localhost:8080/swagger-ui.html (grupos **public** e **admin**)

A API não lê o `.env` sozinha. Para mudar algo (Stripe, logs em JSON, limites), exporte a variável no shell antes, por exemplo `export LOG_FORMAT=ecs`. Pagamento de teste: [Reserva e pagamento](#reserva-e-pagamento-stripe).

### 4. Loja e Admin

Em dois terminais (Node 24):

```bash
cd frontend/familhas-web && npm ci && npm start      # http://localhost:4200
cd frontend/familhas-admin && npm ci && npm start    # http://localhost:4300
```

Usuários de teste do Keycloak:

| Usuário | Senha | Papel |
|---|---|---|
| `user.familhas` | `user12345` | cliente (`ROLE_USER`) |
| `admin.familhas` | `admin1234` | administrador (`ROLE_ADMIN`) |

Sem backend, a Loja também roda com mocks: `npm run start:mock`.

CRM interno (realm `voe-familhas-crm`, [ADR 0013](docs/adr/0013-crm-interno-identidade-e-acesso.md)): `admin.crm` / `AdminCrm.2026` (pede TOTP), `gestor.crm`, `atendente.crm` e `marketing.crm`. A lista completa está em [infra/README.md](infra/README.md#realm-do-crm-interno-voe-familhas-crm). O CRM roda no `familhas-admin` (`http://localhost:4300`), com login no realm do CRM; veja [frontend/familhas-admin/README.md](frontend/familhas-admin/README.md).

### 5. Explorar a API

- Coleção Postman: [docs/postman](docs/postman/README.md) (tokens pelo próprio Keycloak, com PKCE).
- Contratos escritos à mão, fonte dos tipos dos frontends: [docs/api](docs/api).

### 6. Parar

```bash
docker compose stop        # mantém os dados
docker compose down        # remove containers; volumes ficam
docker compose down -v     # apaga também os dados (Oracle, RabbitMQ)
```

Antes de um `down -v`, faça backup: [docs/operacao/backup-restore.md](docs/operacao/backup-restore.md).

## Testes

Cada aplicação tem o próprio build:

```bash
cd backend/familhas-api && ./mvnw verify
cd backend/familhas-email && ./mvnw verify
```

**familhas-api**

| Teste | O que cobre | Requer Docker |
|---|---|---|
| `FamilhasApiApplicationTests` | Contexto Spring com profile `test` (H2) | Não |
| `*ControllerTests` | Controllers (WebMvcTest): filtros, paginação, validação, 401/403/409, claims do onboarding | Não |
| `*ServiceTests`, `CommonUtilitiesTests` | Regras de serviço com Mockito, moeda, ordenação, evento no primeiro login | Não |
| `Outbox*Tests` (unitários) | Gravação no outbox, publisher (lote, backoff, `FAILED`), propriedades AMQP | Não |
| `UserRegisteredEventContractTests` | JSON publicado contra `docs/events/user-registered.v1.schema.json` | Não |
| `OracleMigrationIntegrationTests` | Migrations V1..V13, rota e preço do voo (V9), mapeamento JPA, seed, filtros, N+1, exclusão e vínculo com Keycloak em Oracle real | Sim (é pulado sem Docker) |
| `OutboxMessagingIntegrationTests` | Oracle + RabbitMQ: publicação com confirm, rollback, mensagem sem fila, evento só no primeiro login | Sim (é pulado sem Docker) |
| `PricingServiceTests`, `MoneyTests` | Regras de preço parametrizadas: margem, promoção, taxa, arredondamento HALF_EVEN, moedas | Não |
| `ProviderGatewayTests`, `MockProvidersTests` | Agregação, timeout, circuit breaker, cache e adapters mock | Não |
| `CatalogSearchServiceTests`, `SearchControllerTests` | Busca: validação, faixa de preço, quartos e diárias, paginação e ordenação | Não |
| `QuoteServiceTests`, `QuoteControllerTests` | Cotação por pacote e avulsa, 401/404/422, expiração | Não |
| `CatalogSearchIntegrationTests` | Oracle: provedores curado e mock, busca de voos e pacotes com o seed V9 | Sim (é pulado sem Docker) |
| `QuoteIntegrationTests` | Oracle: cotação persistida (V10), isolamento por cliente, moedas diferentes | Sim (é pulado sem Docker) |
| `CheckoutOpenApiContractTests` | `docs/api/checkout.yaml` é um OpenAPI 3.1 válido, com o webhook público | Não |
| `Booking*Tests`, `Payment*Tests`, `Checkout*Tests` (unitários) | Máquinas de estado, reserva (BRL, assentos, 409/422), checkout idempotente, webhook (assinatura, 400/500), processamento (estorno automático, valor divergente), reconciliação | Não |
| `StripePaymentGatewayTests` | Assinatura `Stripe-Signature`, replay velho, tradução dos eventos, centavos sem arredondamento | Não |
| `CheckoutEventsContractTests` | `BookingCreated`, `PaymentConfirmed` e `PaymentFailed` contra os schemas em `docs/events` | Não |
| `BookingIntegrationTests` | Oracle: reserva (V11), assentos bloqueados e devolvidos, uma reserva por cotação, USD recusado | Sim (é pulado sem Docker) |
| `StripeMockGatewayIntegrationTests` | Adapter Stripe contra o `stripe-mock`: sessão de cartão e Pix, expirar, consultar, estornar | Sim (é pulado sem Docker) |
| `AdminSecurityTests` | **Cliente comum recebe 403** (e anônimo 401) em toda rota `/api/admin/**` registrada; toda rota admin está em `docs/api/admin.yaml` | Não |
| `CrmSecurityTests`, `JwtValidationTests`, `CrmRoleTests`, `Crm*ConverterTests`, `StoreJwtConverterTests`, `CrmPermissionLiteralsTests` | CRM (ADR 0013): toda rota `/api/crm` dá 401 sem token e 403 para cliente e admin da Loja; issuer escolhe decoder e papéis; assinatura, issuer, audiência e expiração reais; matriz papel × permissão | Não |
| `CrmFoundationIntegrationTests`, `CrmOpenApiContractTests`, `StoredFileTypeTests`, `CrmFileRulesTests`, `LocalFileStorageTests`, `FileLinkSignerTests`, `OutboundUrlPolicyTests`, `IntegrationRegistryTests` | Fundação do CRM (ADR 0014): V18, upload pelo conteúdo, permissões por categoria, link assinado, anti-SSRF, integrações não configuradas, contrato `crm.yaml` | Só o de integração |
| `CrmTravelIntegrationTests`, `FlightSearchServiceTests`, `SerpApiFlightSearchGatewayTests`, `SearchTextAndCpfTests`, `VoucherDetailsTests` | Viagens do CRM (ADR 0015): V19..V21, busca sem acento, CPF mascarado, 409, vouchers por tipo e A4 auditado, pesquisa com teto/cota/cache e contrato da SerpApi, painel por papel | Só o de integração |
| `CrmMarketingIntegrationTests`, `GraphMetaGatewayTests`, `ItineraryRulesTests` | Marketing do CRM (ADR 0016): V22, logo, fila do Instagram com claim atômico e sem reenvio após `media_publish`, contrato da Graph API, roteiros e IA com 503 | Só o de integração |
| `CrmAdministrationIntegrationTests`, `TeamServiceTests`, `KeycloakTeamDirectoryTests`, `BackupCipherTests` | Administração do CRM (ADR 0017): V23, backup com hash, restauração sem sobrescrever, e-mail só com link, equipe pela Admin API do Keycloak | Só o de integração |
| `AdminOpenApiContractTests` | `docs/api/admin.yaml` é um OpenAPI 3.1 válido, só com rotas admin e bearer token | Não |
| `Admin*ControllerTests`, `AdminAuditInterceptorTests`, `PricingSettingsServiceTests`, `AdminDashboardServiceTests`, `PaymentExportWriterTests`, `PaymentFeedListenerTests` | Filtros e validação do Admin, auditoria, margem em cache, receita por dia, XLSX/CSV (injeção de fórmula), SSE e leitura dos eventos | Não |
| `AdminIntegrationTests` | Oracle: V14..V16, filtros de reservas e pagamentos, abas de clientes, exportação auditada, dashboard, margem salva mudando a próxima cotação, cotação manual vista pelo cliente sem a nota interna | Sim (é pulado sem Docker) |
| `PaymentFeedIntegrationTests` | RabbitMQ: `PaymentConfirmed` publicado no exchange chega à fila anônima da planilha | Sim (é pulado sem Docker) |
| `PaymentFlowIntegrationTests` | Oracle + stripe-mock, HTTP com JWT: cotação → reserva → checkout → webhook → `CONFIRMED`, replay sem duplicar, Pix, recusa, estorno parcial e total, webhook forjado, expiração | Sim (é pulado sem Docker) |
| `Trip*Tests`, `Favorite*Tests`, `RatingServiceClientTests`, `Recommendation*Tests`, `RuleBasedRecommendationServiceTests` | Viagem futura x passada pela data, pagamento e parcelas, 401/403/404 com `code`, favoritos idempotentes, regras de avaliação, pesos e motivos da recomendação (parametrizados) | Não |
| `MinhasViagensOpenApiContractTests` | `docs/api/minhas-viagens.yaml` é um OpenAPI 3.1 válido e tudo exige token | Não |
| `CorrelationIdFilterTests`, `RateLimiterTests` | correlationId aceito ou trocado (log injection), token bucket, política por rota | Não |
| `ApiHardeningTests` | Contexto inteiro: headers de segurança, 429 por IP e por usuário, webhook fora do limite, CORS com `X-Correlation-Id`, Actuator só ADMIN | Não |
| `GeneratedOpenApiGroupsTests` | Swagger por grupo: public sem rota admin, admin só `/api/admin`, bearer nos dois | Não |
| `PostmanCollectionTests` | Toda requisição da coleção bate numa rota real e só usa query params do contrato | Não |
| `RetentionIntegrationTests` | Oracle (V17): retenção em lotes de outbox, webhooks e cotações vencidas, sem tocar em FAILED, PENDING ou cotação reservada | Sim (é pulado sem Docker) |
| `MinhasViagensIntegrationTests` | Oracle (V13), HTTP com JWT: cada cliente só vê e só mexe nas próprias viagens, avaliações e favoritos; regra de viagem passada no banco; recomendação sem pacote reservado ou favoritado | Sim (é pulado sem Docker) |

**familhas-email**

| Teste | O que cobre | Requer Docker |
|---|---|---|
| `EmailSenderTests` | Envio UTF-8 e classificação de falhas SMTP (transitória x permanente) | Não |
| `EventMessageReaderTests`, `IdempotentEventProcessorTests`, `WelcomeEmailServiceTests` | Validação do payload, idempotência e texto do e-mail | Não |
| `UserRegisteredEventContractTests` | Consumidor contra o mesmo schema da API | Não |
| `UserRegisteredFlowIntegrationTests` | Oracle + RabbitMQ + MailHog: e-mail entregue, reprocessamento sem duplicar, DLQ para payload inválido, logs sem dados pessoais | Sim (é pulado sem Docker) |
| `EmailFailureHandlingIntegrationTests` | Retry com backoff, tentativas esgotadas e falha permanente indo para a DLQ, reprocessamento a partir da DLQ | Sim (é pulado sem Docker) |
| `CheckoutEmailsTests`, `CheckoutEventsContractTests` | Textos dos e-mails de reserva e pagamento; consumidor contra os schemas de reserva e pagamento | Não |
| `ObservabilityAndRetentionIntegrationTests` | Oracle + RabbitMQ: métrica de profundidade da DLQ e contador de mensagens na DLQ; retenção de `processed_event` | Sim (é pulado sem Docker) |
| `CheckoutEmailFlowIntegrationTests` | Oracle + RabbitMQ + MailHog: os três e-mails, reprocessamento sem duplicar, DLQ para valor inválido | Sim (é pulado sem Docker) |

## API: áreas e segurança

Detalhes em [ADR 0004](docs/adr/0004-seguranca-keycloak-rotas-problem-details.md).

| Área | Prefixo | Acesso |
|---|---|---|
| Catálogo da Loja | `/api/...` | Público, só GET, só itens ativos |
| Área do cliente | `/api/me`, `/api/me/quotes`, `/api/me/bookings`, `/api/me/trips`, `/api/me/favorites`, `/api/me/ratings`, `/api/me/recommendations` | Token com `ROLE_USER` ou `ROLE_ADMIN` |
| Administração | `/api/admin/...` | Token com `ROLE_ADMIN`; toda escrita e exportação vai para a auditoria ([ADR 0011](docs/adr/0011-admin.md)) |
| Webhook do Stripe | `POST /api/webhooks/stripe` | Público; autenticado pela `Stripe-Signature` |
| Health e info | `/actuator/health`, `/actuator/info` | Público (só o status; detalhes com `ROLE_ADMIN`) |
| Métricas | `/actuator/metrics`, `/actuator/prometheus` | Token com `ROLE_ADMIN` |
| OpenAPI | `/swagger-ui.html`, `/v3/api-docs/{public,admin}` | Público, só no perfil local ([ADR 0012](docs/adr/0012-qualidade-final.md)) |

Tokens são emitidos pelo Keycloak (realm `familhas`). A API valida assinatura, issuer e a audiência `familhas-api`. Sem token, a resposta é 401; sem permissão, 403. Todos os erros saem como Problem Details (`application/problem+json`), com `title`, `status` e `detail`.

## API: listas paginadas

Todo `GET` de lista é paginado e aceita filtros opcionais ([ADR 0003](docs/adr/0003-catalogo-paginacao-auditoria.md)):

```
GET /api/travel-packages?destinationCityId=...&maxPrice=3000&q=resort&page=0&size=20&sort=price,asc
```

A resposta tem o formato `{ "content": [...], "page": 0, "size": 20, "totalElements": 2, "totalPages": 1 }`. O `size` máximo é 100. Campo de ordenação fora da lista branca do recurso retorna 400. No catálogo público o filtro `status` é ignorado: só aparecem itens ativos.

| Recurso (público em `/api`, admin em `/api/admin`) | Filtros | Ordenação |
|---|---|---|
| `/travel-packages` | originCityId, destinationCityId, categoryId, status, minPrice, maxPrice, q, onPromotion | title, price, createdAt, updatedAt |
| `/hotels` | cityId, minStars, status, q | name, stars, createdAt, updatedAt |
| `/room-offers` | hotelId, cityId, boardType, guests, maxPricePerNight, status | pricePerNight, maxGuests, roomType, createdAt, updatedAt |
| `/car-rental-offers` | cityId, carCategory, transmission, minSeats, maxPricePerDay, status | pricePerDay, seats, companyName, createdAt, updatedAt |
| `/cities` | cityType, status, q | name, country, createdAt, updatedAt |
| `/categories` | status, q | title, createdAt, updatedAt |
| `/flights` | departureFrom, departureTo (ISO), originCityId, destinationCityId, status, q | boardingDate, returnDate, companyName, pricePerPassenger, createdAt, updatedAt |
| `/clients` (só admin) | status, q (nome ou e-mail), segment (NEW, ACTIVE, PAST) | name, email, createdAt, updatedAt |
| `/ratings` (só admin) | clientId, travelPackageId | rating, createdAt, updatedAt |

## Busca e cotação

Detalhes em [ADR 0006](docs/adr/0006-provedores-preco-e-cotacao.md).

**Busca (pública)**: voos, hotéis e carros vêm dos provedores (catálogo curado e, com `PROVIDERS_MOCK_ENABLED=true`, ofertas simuladas). Os preços já incluem a margem; taxa de serviço e promoção entram na cotação.

| Endpoint | Parâmetros | Ordenação |
|---|---|---|
| `GET /api/search/flights` | originCityId*, destinationCityId*, departureDate*, returnDate, passengers (1-9), minPrice, maxPrice | totalPrice, departureAt |
| `GET /api/search/hotels` | cityId*, checkIn*, checkOut* (1-30 noites), guests (1-9), minStars, minPrice, maxPrice | totalPrice, stars |
| `GET /api/search/cars` | cityId*, pickUpDate*, dropOffDate*, passengers, carCategory, minPrice, maxPrice | totalPrice, seats |
| `GET /api/search/packages` | originCityId, destinationCityId, categoryId, departureFrom, departureTo, passengers, minPrice, maxPrice | totalPrice, departureAt |

`*` obrigatório. minPrice e maxPrice valem sobre o total do grupo. A resposta tem o formato paginado de sempre, mais `unavailableProviders` (provedores que não responderam; o resto do resultado continua valendo).

```
GET /api/search/flights?originCityId=c1770000-0000-4000-8000-000000000001&destinationCityId=c1770000-0000-4000-8000-000000000002&departureDate=2027-03-10&passengers=2
```

**Cotação (autenticada, `/api/me/quotes`)**

- `POST` com `{"travelPackageId": "...", "passengers": 2}` ou com itens avulsos da busca: `{"passengers": 2, "flight": {"offerId": "..."}, "hotel": {"offerId": "...", "checkIn": "...", "checkOut": "..."}, "car": {"offerId": "...", "pickUpDate": "...", "dropOffDate": "..."}}`. Responde 201 com `Location`.
- Os preços são reconsultados no provedor; o cliente nunca envia preço. A resposta traz itens, `subtotal` (já com margem), `discount`, `serviceFee`, `total` e `expiresAt` (24 h).
- `GET /api/me/quotes` lista as cotações do cliente; `GET /api/me/quotes/{id}` mostra uma. Cotação de outro cliente responde 404. Depois do prazo, o status vira `EXPIRED`.
- Erros: oferta indisponível, assentos insuficientes ou moedas diferentes = 422; provedor fora do ar = 503.

**Preço**: total = componentes + margem − desconto + taxa, em `BigDecimal` com HALF_EVEN. Margem e taxa se editam no Admin (Catálogo > Margem e taxa, tabela `pricing_settings`); enquanto ninguém salvar, valem `PRICING_MARGIN_PERCENT` (padrão 10) e `PRICING_SERVICE_FEE_PER_PASSENGER` (padrão 0).

## Reserva e pagamento (Stripe)

Decisões em [ADR 0007](docs/adr/0007-reserva-checkout-e-webhooks-stripe.md) e [ADR 0008](docs/adr/0008-parcelamento-no-cartao.md); contrato HTTP em [docs/api/checkout.yaml](docs/api/checkout.yaml).

1. `POST /api/me/bookings` com `{"quoteId": "..."}` cria a reserva `PENDING_PAYMENT` a partir de uma cotação `OPEN` em BRL. Os assentos do voo ficam bloqueados por 30 min (`BOOKING_PAYMENT_WINDOW`).
2. `POST /api/me/bookings/{id}/checkout-session` com `{"paymentMethod": "CREDIT_CARD"}` ou `"PIX"` devolve a `checkoutUrl` do Stripe Checkout hospedado. Dados de cartão nunca passam pela API. Pagamento sempre à vista (parcelamento adiado, ADR 0008).
3. O Stripe chama `POST /api/webhooks/stripe`. Com a assinatura válida, o pagamento aprovado confirma a reserva (`CONFIRMED`). Cada `event.id` é processado uma vez.
4. A Loja volta para `/checkout/{bookingId}/sucesso` e consulta `GET /api/me/bookings/{id}` até a reserva sair de `PENDING_PAYMENT`.
5. A reconciliação (a cada 5 min) consulta no Stripe os pagamentos parados e cancela reservas vencidas sem pagamento, devolvendo os assentos.

Erros de negócio trazem um `code` estável no Problem Details (ex.: `QUOTE_EXPIRED`, `SEATS_UNAVAILABLE`, `PAYMENT_IN_PROGRESS`).

**Testando com a conta Stripe de teste**

A API só aceita chaves de teste (`sk_test_`/`rk_test_`) e não sobe com chave live. Sem `STRIPE_SECRET_KEY`, ela sobe normalmente e o checkout responde 503.

```bash
stripe login
stripe listen --forward-to localhost:8080/api/webhooks/stripe   # imprime o whsec_... desta sessão

# em outro terminal
export STRIPE_SECRET_KEY=sk_test_...      # Dashboard do Stripe, modo de teste
export STRIPE_WEBHOOK_SECRET=whsec_...    # o impresso pelo stripe listen
cd backend/familhas-api && ./mvnw spring-boot:run
```

Abra a `checkoutUrl` devolvida pelo checkout e pague com um [cartão de teste](https://docs.stripe.com/testing) (ex.: `4242 4242 4242 4242`, qualquer data futura e CVC). Os e-mails de reserva e pagamento aparecem no MailHog (`http://localhost:8025`). Para Pix, a conta precisa ter o método habilitado; com `STRIPE_PIX_ENABLED=false` a API responde 422 `PAYMENT_METHOD_UNAVAILABLE`.

## Minhas viagens e recomendação

Decisões em [ADR 0010](docs/adr/0010-minhas-viagens-e-recomendacao.md); contrato HTTP em [docs/api/minhas-viagens.yaml](docs/api/minhas-viagens.yaml). Tudo é do dono do token; recurso de outro cliente responde 404.

- `GET /api/me/trips?when=UPCOMING|PAST`: viagens = reservas `CONFIRMED` ou `COMPLETED`, sem tabela própria. Passada quando `COMPLETED` ou quando o último dia já passou (America/Sao_Paulo). Cada viagem traz itens, total, pagamento (status, método, parcelas) e a avaliação do cliente. `GET /api/me/trips/{id}` devolve uma.
- `GET /api/me/favorites`, `PUT` e `DELETE /api/me/favorites/{packageId}`: favoritos, idempotentes; só pacote ativo entra (`PACKAGE_NOT_FOUND`).
- `/api/me/ratings`: só pacote de viagem passada pode ser avaliado (`RATING_NOT_ALLOWED`), uma vez por pacote (`RATING_ALREADY_EXISTS`).
- `GET /api/me/recommendations?limit=6`: recomendação v1 por regras (`RecommendationService`): categoria e país de viagens, favoritos e avaliações, época do ano, média de avaliações e promoção, com `reasons`. Não repete pacote reservado, favoritado ou avaliado. A v2 (ML/embeddings) está só documentada no ADR.

## Mensageria e onboarding

Detalhes em [ADR 0005](docs/adr/0005-mensageria-outbox-e-familhas-email.md) e contratos em [docs/events](docs/events/README.md).

1. No primeiro acesso a `/api/me` de um usuário novo (e-mail verificado no Keycloak), a API cria o `Client` e grava o `UserRegisteredEvent` na tabela `outbox_event`, na mesma transação.
2. O publisher do outbox envia o evento ao exchange `familhas.events` (routing key `client.user-registered.v1`) e só marca como publicado depois do confirm do RabbitMQ.
3. O `familhas-email` consome a fila `familhas-email.user-registered`, valida o payload, registra o `eventId` em `processed_event` e envia as boas-vindas pelo SMTP (MailHog em `http://localhost:8025`).
4. A mesma mensagem reentregue não gera um segundo e-mail. Payload inválido e falha permanente vão para `familhas-email.user-registered.dlq`.

Vincular um cliente já cadastrado pelo Enéas e logins seguintes não geram evento. Os logs usam só `eventId` e `correlationId`, sem e-mail ou nome (LGPD).

Reserva e pagamento seguem o mesmo caminho: `BookingCreated`, `PaymentConfirmed` e `PaymentFailed` saem pelo outbox e o `familhas-email` envia um e-mail para cada um, com fila e DLQ próprias (`familhas-email.booking-created`, `familhas-email.payment-confirmed`, `familhas-email.payment-failed`).

## IDs

Todas as entidades usam UUID, armazenado como `RAW(16)` no Oracle ([ADR 0002](docs/adr/0002-ids-uuid-raw16.md)). Os registros de seed têm UUIDs fixos, por exemplo a cidade São Paulo é `c1770000-0000-4000-8000-000000000001`.

## Loja (familhas-web)

Detalhes em [ADR 0009](docs/adr/0009-frontend-da-loja.md) e no [README do familhas-web](frontend/familhas-web/README.md).

```bash
cd frontend/familhas-web
npm ci
npm run start:mock   # sem backend: mocks (MSW) e login simulado
npm start            # com a API em 8080 e o Keycloak em 8443
npm run lint && npm run test:ci && npm run build
```

- Login e logout pelo Keycloak (client `familhas-web`, Authorization Code + PKCE). O token só vai para a API.
- URLs, Keycloak e número do WhatsApp ficam em `public/config.json`, por ambiente, sem rebuild.
- Contratos em `docs/api`: `familhas-api.yaml` (catálogo e busca), `checkout.yaml` (cotação, reserva e pagamento, Fase 6) e `minhas-viagens.yaml` (Fase 8). Os tipos do front são gerados deles.
- "Minhas viagens" fica em `/minhas-viagens` (só logado), com link no cabeçalho; o coração de favoritar aparece nos cartões e no detalhe do pacote.

## Admin (familhas-admin)

Detalhes em [ADR 0011](docs/adr/0011-admin.md), contrato em [docs/api/admin.yaml](docs/api/admin.yaml) e [README do familhas-admin](frontend/familhas-admin/README.md).

```bash
cd frontend/familhas-admin
npm ci
npm start            # http://localhost:4300, com a API em 8080 e o Keycloak em 8443
npm run lint && npm run test:ci && npm run build
```

- Login no Keycloak pelo client `familhas-admin` (PKCE). Só entra quem tem `ROLE_ADMIN` (usuário de teste `admin.familhas`); cliente comum vê "sem permissão" e a API responde 403.
- Telas: dashboard, clientes (abas novos, ativos e passados, busca e detalhe), reservas, planilha de pagamentos, cotações (inclusive manuais), catálogo (voos, hotéis, quartos, carros, pacotes, promoções, margem e taxa) e auditoria.
- A planilha de pagamentos atualiza sozinha: avisos por SSE (`/api/admin/payments/stream`) a partir dos eventos `payment.*` e recarga a cada 30 s. Exporta XLSX ou CSV com os filtros da tela.
- Variáveis novas da API: `ADMIN_PAYMENT_FEED_ENABLED` (padrão `true`) e `MVC_ASYNC_REQUEST_TIMEOUT` (padrão `PT10M`, para exportações grandes).

## Observabilidade

Detalhes em [ADR 0012](docs/adr/0012-qualidade-final.md).

- Toda resposta da API tem `X-Correlation-Id`. Mande o mesmo header para seguir a requisição: o id vai para os logs da API, para o outbox, para a mensagem no RabbitMQ e para os logs do `familhas-email`.
- `LOG_FORMAT=ecs` (ou `logstash`) liga logs JSON nos dois serviços; vazio mantém o texto legível.
- Métricas do Actuator em `/actuator/metrics` e `/actuator/prometheus`: na API exigem token `ROLE_ADMIN`; no `familhas-email`, porta 8081 (só rede interna fora do local). As da Familhas: `familhas_outbox_events{status}`, `familhas_outbox_published_total`, `familhas_ratelimit_rejected_total`, `familhas_retention_deleted_total`, `familhas_email_dlq_messages` e `familhas_email_dead_lettered_total`.
- Retenção diária: `outbox_event` publicados (30 dias), `stripe_webhook_events` (90), cotações vencidas sem reserva (90) e `processed_event` (90). Reservas e pagamentos nunca são apagados.

## Segurança

- Revisão OWASP Top 10, pendências e CSP recomendada para os frontends: [docs/seguranca/revisao-owasp.md](docs/seguranca/revisao-owasp.md).
- Rate limit por minuto, por usuário ou IP: 300 no catálogo e na leitura do cliente, 30 na escrita do cliente, 600 no Admin. Acima disso, 429 com `Retry-After` (`RATE_LIMIT_*`).
- Headers: CSP restrita, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS em HTTPS.
- Swagger só no perfil local (`SPRINGDOC_ENABLED`). Health público só com o status; detalhes só para ADMIN.
- Dependabot semanal para Maven, npm, Actions e Docker.

## Backup e restore

```bash
infra/oracle/backup.sh                                   # dump dos 3 schemas em backups/
infra/oracle/restore.sh --verify backups/familhas-...dmp # testa o dump em schemas temporários
infra/oracle/restore.sh backups/familhas-...dmp          # restore (apaga os dados atuais)
```

Passo a passo em [docs/operacao/backup-restore.md](docs/operacao/backup-restore.md).

## Convenções

Branches, Conventional Commits e o que conferir antes do PR: [CONTRIBUTING.md](CONTRIBUTING.md).

- Uma branch por fase: `feat/fase-N-descricao`. Nada direto na `main`.
- Commits no padrão [Conventional Commits](https://www.conventionalcommits.org/), em português, com escopo: `feat(admin): ...`, `fix(keycloak): ...`, `docs(checklist): ...`.
- Decisões relevantes viram um ADR curto em `docs/adr/`.
