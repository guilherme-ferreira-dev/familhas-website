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
| `CityControllerTests` | Controller com IDs UUID e segurança | Não |
| `OracleMigrationIntegrationTests` | Migrations V1..V4, mapeamento JPA e seed em Oracle real (Testcontainers) | Sim (é pulado sem Docker) |

## IDs

Todas as entidades usam UUID, armazenado como `RAW(16)` no Oracle ([ADR 0002](docs/adr/0002-ids-uuid-raw16.md)). Os registros de seed têm UUIDs fixos, por exemplo a cidade São Paulo é `c1770000-0000-4000-8000-000000000001`.

## Convenções

- Uma branch por fase: `feat/fase-N-descricao`. Nada direto na `main`.
- Commits no padrão [Conventional Commits](https://www.conventionalcommits.org/), em português: `feat: adicionando ...`, `fix: corrigindo ...`, `refactor: ...`, `docs: ...`, `test: ...`, `ci: ...`.
- Decisões relevantes viram um ADR curto em `docs/adr/`.
