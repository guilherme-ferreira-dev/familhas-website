# Infra local

Este diretório concentra o bootstrap da stack local da aplicação (`oracle`, `rabbitmq`, `keycloak` e `mailhog`) sem configuração manual no painel do Keycloak.

## Subida da infraestrutura

Na raiz do repositório:

```bash
docker compose up -d --wait
```

O `--wait` só retorna quando todos os serviços estão `healthy`. Na primeira subida o Oracle leva alguns minutos. O `compose.yaml` da raiz inclui este `infra/docker-compose.yml` e fixa o nome do projeto como `familhas`.

Para conferir o estado:

```bash
docker compose ps
```

## Serviços, portas e healthchecks

| Serviço | Porta(s) no host | Healthcheck | Persistência |
|---|---|---|---|
| Oracle Free 23 | 1521 | `healthcheck.sh` da imagem | volume `familhas_oracle_data` |
| RabbitMQ 3.13 | 5672 (AMQP), 15672 (painel) | `rabbitmq-diagnostics check_running` | volume `familhas_rabbitmq_data` |
| Keycloak 26.4 | 8443 (HTTPS) | `GET /health/ready` na porta de gerenciamento 9000 | schema `KEYCLOAK` no Oracle |
| MailHog | 1025 (SMTP), 8025 (UI) | `GET /` na UI | memória (descartável) |

O Keycloak depende do Oracle `healthy`. A porta 9000 do Keycloak não é publicada no host. Ela fica só na rede do Docker, em HTTP (`KC_HTTP_MANAGEMENT_SCHEME=http`), para o healthcheck funcionar sem curl na imagem.

Para parar sem perder dados: `docker compose down`. Para zerar tudo, incluindo os bancos: `docker compose down -v`.

## O que sobe automaticamente no Keycloak

- Realm `familhas` importado de `infra/keycloak/data/import/familhas-realm.json` (versionado no Git). A importação ignora o realm se ele já existir.
- Roles: `ROLE_USER`, `ROLE_ADMIN`
- Clients:
  - `familhas-api` (confidential)
  - `familhas-web` (public)
- Usuários de teste:
  - `admin.familhas` / `admin123`
  - `user.familhas` / `user123`

## Imagem do Keycloak

- Construída por `infra/keycloak/Dockerfile`, que adiciona o `ojdbc17.jar` (driver Oracle) e roda `kc.sh build` com `KC_DB=oracle` e `KC_HEALTH_ENABLED=true`.
- O container sobe com `start --optimized`, sem rebuild a cada start.
- Depois de mudar o Dockerfile ou o script: `docker compose up -d --build`.

## HTTPS local do Keycloak

- O container executa `infra/keycloak/scripts/start-with-keystore.sh`.
- Se `infra/keycloak/keystore/keystore.p12` não existir, o script gera um keystore self-signed para uso local. O arquivo é ignorado pelo Git.
- Porta exposta: `8443` (ou `KEYCLOAK_HTTPS_PORT` definida no `.env`).

## Endpoints úteis para validação

- Keycloak Admin Console: `https://localhost:8443`
- OpenID configuration: `https://localhost:8443/realms/familhas/.well-known/openid-configuration`
- Token endpoint: `https://localhost:8443/realms/familhas/protocol/openid-connect/token`
- RabbitMQ: `http://localhost:15672` (guest/guest)
- MailHog: `http://localhost:8025`
- API (com `mvnw spring-boot:run` em `backend/familhas-api`): `http://localhost:8080/actuator/health`

## Observação sobre Oracle

- O Keycloak usa o mesmo Oracle, com usuário/schema dedicado (`KEYCLOAK` por padrão), criado via `infra/oracle/init/01-create-keycloak-user.sql`.
- Os scripts de `infra/oracle/init` só rodam na primeira criação do volume.
