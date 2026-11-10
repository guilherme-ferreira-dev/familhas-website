# Familhas

Plataforma da agência de viagens Familhas: **Loja** (busca, cotação, compra e "Minhas viagens") e **Admin** (gestão de clientes, reservas e pagamentos).

## Estrutura

```
backend/familhas-api     API Spring Boot (Java 21), monólito modular
infra/                   Docker Compose: Oracle, RabbitMQ, Keycloak, MailHog
docs/adr/                Registro de decisões de arquitetura
.github/workflows/       CI (build e testes)
```

Entram em fases futuras: `backend/familhas-email` (Fase 4), `frontend/familhas-web` (Fase 7) e `frontend/familhas-admin` (Fase 10).

## Pré-requisitos

- Java 21
- Docker Desktop (infraestrutura local e testes de integração com Oracle)

Não é preciso instalar Maven: use o wrapper (`mvnw` / `mvnw.cmd`).

## Executando localmente

1. Crie o arquivo `.env` na raiz a partir do `.env.example` e ajuste os valores. O `.env` é ignorado pelo Git.

2. Suba a infraestrutura na raiz do repositório:

   ```bash
   docker compose up -d --wait
   ```

   O `--wait` só retorna quando Oracle, RabbitMQ, Keycloak e MailHog estão `healthy`. Na primeira execução o Oracle leva alguns minutos. Detalhes de Keycloak, HTTPS e usuários de teste estão em [infra/README.md](infra/README.md).

3. Rode a API:

   ```bash
   cd backend/familhas-api
   ./mvnw spring-boot:run        # Linux/macOS
   mvnw.cmd spring-boot:run      # Windows
   ```

   O Flyway aplica as migrations ao subir, e o Hibernate valida o schema (`ddl-auto=validate`).

4. Verifique: `GET http://localhost:8080/actuator/health` deve retornar `UP`.

## Testes

```bash
cd backend/familhas-api
./mvnw verify
```

| Teste | O que cobre | Requer Docker |
|---|---|---|
| `FamilhasApiApplicationTests` | Contexto Spring com profile `test` (H2) | Não |
| `*ControllerTests` | Controllers (WebMvcTest): filtros, paginação, validação, 401/403/409 | Não |
| `*ServiceTests`, `CommonUtilitiesTests` | Regras de serviço com Mockito, moeda, ordenação | Não |
| `OracleMigrationIntegrationTests` | Migrations V1..V7, mapeamento JPA, seed, filtros, N+1, exclusão e vínculo com Keycloak em Oracle real (Testcontainers) | Sim (é pulado sem Docker) |

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
| `/flights` | departureFrom, departureTo (ISO), q | boardingDate, returnDate, companyName, createdAt, updatedAt |
| `/clients` (só admin) | status, q (nome ou e-mail) | name, email, createdAt, updatedAt |
| `/ratings` (só admin) | clientId, travelPackageId | rating, createdAt, updatedAt |

## IDs

Todas as entidades usam UUID, armazenado como `RAW(16)` no Oracle ([ADR 0002](docs/adr/0002-ids-uuid-raw16.md)). Os registros de seed têm UUIDs fixos, por exemplo a cidade São Paulo é `c1770000-0000-4000-8000-000000000001`.

## Convenções

- Uma branch por fase: `feat/fase-N-descricao`. Nada direto na `main`.
- Commits no padrão [Conventional Commits](https://www.conventionalcommits.org/), em português: `feat: adicionando ...`, `fix: corrigindo ...`, `refactor: ...`, `docs: ...`, `test: ...`, `ci: ...`.
- Decisões relevantes viram um ADR curto em `docs/adr/`.
