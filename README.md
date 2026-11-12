# Familhas

Plataforma da agência de viagens Familhas: **Loja** (busca, cotação, compra e "Minhas viagens") e **Admin** (gestão de clientes, reservas e pagamentos).

## Estrutura

```
backend/familhas-api     API Spring Boot (Java 21), monólito modular, produtora de eventos (outbox)
backend/familhas-email   Consumidor de eventos que envia e-mails transacionais (boas-vindas)
infra/                   Docker Compose: Oracle, RabbitMQ, Keycloak, MailHog e familhas-email
docs/adr/                Registro de decisões de arquitetura
docs/events/             Contratos versionados dos eventos (JSON Schema)
docs/checklist.md        Checklist das fases
.github/workflows/       CI (build e testes de cada aplicação)
```

Entram em fases futuras: `frontend/familhas-web` (Fase 7) e `frontend/familhas-admin` (Fase 10).

## Pré-requisitos

- Java 21
- Docker Desktop (infraestrutura local e testes de integração com Oracle)

Não é preciso instalar Maven: use o wrapper (`mvnw` / `mvnw.cmd`).

## Executando localmente

1. Crie o arquivo `.env` na raiz a partir do `.env.example`. O `.env` é ignorado pelo Git e nunca deve ser commitado. Variável vazia usa o padrão local do compose.

   ```bash
   cp .env.example .env
   ```

2. Suba a infraestrutura na raiz do repositório:

   ```bash
   docker compose up -d --wait
   ```

   O `--wait` só retorna quando Oracle, RabbitMQ, Keycloak, MailHog e `familhas-email` estão `healthy`. Na primeira execução o Oracle leva alguns minutos e a imagem do `familhas-email` é construída a partir do código. Detalhes de Keycloak, HTTPS, filas e usuários de teste estão em [infra/README.md](infra/README.md).

   Depois de mudar o código do `familhas-email`, reconstrua a imagem com `docker compose up -d --build familhas-email`.

3. Rode a API:

   ```bash
   cd backend/familhas-api
   ./mvnw spring-boot:run        # Linux/macOS
   mvnw.cmd spring-boot:run      # Windows
   ```

   O Flyway aplica as migrations ao subir, e o Hibernate valida o schema (`ddl-auto=validate`).

4. Verifique: `GET http://localhost:8080/actuator/health` deve retornar `UP`.

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
| `OracleMigrationIntegrationTests` | Migrations V1..V10, rota e preço do voo (V9), mapeamento JPA, seed, filtros, N+1, exclusão e vínculo com Keycloak em Oracle real | Sim (é pulado sem Docker) |
| `OutboxMessagingIntegrationTests` | Oracle + RabbitMQ: publicação com confirm, rollback, mensagem sem fila, evento só no primeiro login | Sim (é pulado sem Docker) |
| `PricingServiceTests`, `MoneyTests` | Regras de preço parametrizadas: margem, promoção, taxa, arredondamento HALF_EVEN, moedas | Não |
| `ProviderGatewayTests`, `MockProvidersTests` | Agregação, timeout, circuit breaker, cache e adapters mock | Não |
| `CatalogSearchServiceTests`, `SearchControllerTests` | Busca: validação, faixa de preço, quartos e diárias, paginação e ordenação | Não |
| `QuoteServiceTests`, `QuoteControllerTests` | Cotação por pacote e avulsa, 401/404/422, expiração | Não |
| `CatalogSearchIntegrationTests` | Oracle: provedores curado e mock, busca de voos e pacotes com o seed V9 | Sim (é pulado sem Docker) |
| `QuoteIntegrationTests` | Oracle: cotação persistida (V10), isolamento por cliente, moedas diferentes | Sim (é pulado sem Docker) |

**familhas-email**

| Teste | O que cobre | Requer Docker |
|---|---|---|
| `EmailSenderTests` | Envio UTF-8 e classificação de falhas SMTP (transitória x permanente) | Não |
| `EventMessageReaderTests`, `IdempotentEventProcessorTests`, `WelcomeEmailServiceTests` | Validação do payload, idempotência e texto do e-mail | Não |
| `UserRegisteredEventContractTests` | Consumidor contra o mesmo schema da API | Não |
| `UserRegisteredFlowIntegrationTests` | Oracle + RabbitMQ + MailHog: e-mail entregue, reprocessamento sem duplicar, DLQ para payload inválido, logs sem dados pessoais | Sim (é pulado sem Docker) |
| `EmailFailureHandlingIntegrationTests` | Retry com backoff, tentativas esgotadas e falha permanente indo para a DLQ, reprocessamento a partir da DLQ | Sim (é pulado sem Docker) |

## API: áreas e segurança

Detalhes em [ADR 0004](docs/adr/0004-seguranca-keycloak-rotas-problem-details.md).

| Área | Prefixo | Acesso |
|---|---|---|
| Catálogo da Loja | `/api/...` | Público, só GET, só itens ativos |
| Área do cliente | `/api/me`, `/api/me/ratings` | Token com `ROLE_USER` ou `ROLE_ADMIN` |
| Administração | `/api/admin/...` | Token com `ROLE_ADMIN` |

Tokens são emitidos pelo Keycloak (realm `familhas`). A API valida assinatura, issuer e a audiência `familhas-api`. Sem token, a resposta é 401; sem permissão, 403. Todos os erros saem como Problem Details (`application/problem+json`), com `title`, `status` e `detail`.

## API: listas paginadas

Todo `GET` de lista é paginado e aceita filtros opcionais ([ADR 0003](docs/adr/0003-catalogo-paginacao-auditoria.md)):

```
GET /api/travel-packages?destinationCityId=...&maxPrice=3000&q=resort&page=0&size=20&sort=price,asc
```

A resposta tem o formato `{ "content": [...], "page": 0, "size": 20, "totalElements": 2, "totalPages": 1 }`. O `size` máximo é 100. Campo de ordenação fora da lista branca do recurso retorna 400. No catálogo público o filtro `status` é ignorado: só aparecem itens ativos.

| Recurso (público em `/api`, admin em `/api/admin`) | Filtros | Ordenação |
|---|---|---|
| `/travel-packages` | originCityId, destinationCityId, categoryId, status, minPrice, maxPrice, q | title, price, createdAt, updatedAt |
| `/hotels` | cityId, minStars, status, q | name, stars, createdAt, updatedAt |
| `/room-offers` | hotelId, cityId, boardType, guests, maxPricePerNight, status | pricePerNight, maxGuests, roomType, createdAt, updatedAt |
| `/car-rental-offers` | cityId, carCategory, transmission, minSeats, maxPricePerDay, status | pricePerDay, seats, companyName, createdAt, updatedAt |
| `/cities` | cityType, status, q | name, country, createdAt, updatedAt |
| `/categories` | status, q | title, createdAt, updatedAt |
| `/flights` | departureFrom, departureTo (ISO), originCityId, destinationCityId, status, q | boardingDate, returnDate, companyName, pricePerPassenger, createdAt, updatedAt |
| `/clients` (só admin) | status, q (nome ou e-mail) | name, email, createdAt, updatedAt |
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

**Preço**: total = componentes + margem − desconto + taxa, em `BigDecimal` com HALF_EVEN. Margem e taxa são configuráveis (`PRICING_MARGIN_PERCENT`, padrão 10; `PRICING_SERVICE_FEE_PER_PASSENGER`, padrão 0) e provisórias até o Enéas definir as regras.

## Mensageria e onboarding

Detalhes em [ADR 0005](docs/adr/0005-mensageria-outbox-e-familhas-email.md) e contratos em [docs/events](docs/events/README.md).

1. No primeiro acesso a `/api/me` de um usuário novo (e-mail verificado no Keycloak), a API cria o `Client` e grava o `UserRegisteredEvent` na tabela `outbox_event`, na mesma transação.
2. O publisher do outbox envia o evento ao exchange `familhas.events` (routing key `client.user-registered.v1`) e só marca como publicado depois do confirm do RabbitMQ.
3. O `familhas-email` consome a fila `familhas-email.user-registered`, valida o payload, registra o `eventId` em `processed_event` e envia as boas-vindas pelo SMTP (MailHog em `http://localhost:8025`).
4. A mesma mensagem reentregue não gera um segundo e-mail. Payload inválido e falha permanente vão para `familhas-email.user-registered.dlq`.

Vincular um cliente já cadastrado pelo Enéas e logins seguintes não geram evento. Os logs usam só `eventId` e `correlationId`, sem e-mail ou nome (LGPD).

## IDs

Todas as entidades usam UUID, armazenado como `RAW(16)` no Oracle ([ADR 0002](docs/adr/0002-ids-uuid-raw16.md)). Os registros de seed têm UUIDs fixos, por exemplo a cidade São Paulo é `c1770000-0000-4000-8000-000000000001`.

## Convenções

- Uma branch por fase: `feat/fase-N-descricao`. Nada direto na `main`.
- Commits no padrão [Conventional Commits](https://www.conventionalcommits.org/), em português: `feat: adicionando ...`, `fix: corrigindo ...`, `refactor: ...`, `docs: ...`, `test: ...`, `ci: ...`.
- Decisões relevantes viram um ADR curto em `docs/adr/`.
