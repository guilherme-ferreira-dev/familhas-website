# Infra local

Este diretório concentra o bootstrap da stack local da aplicação (`oracle`, `rabbitmq`, `keycloak`, `mailhog` e `familhas-email`) sem configuração manual no painel do Keycloak.

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
| familhas-email | 8081 (`/actuator/health`) | `GET /actuator/health` = `UP` (inclui banco, RabbitMQ e SMTP) | schema `FAMILHAS_EMAIL` no Oracle |

O Keycloak depende do Oracle `healthy`. O `familhas-email` depende de Oracle, RabbitMQ e MailHog `healthy`. A imagem dele é construída a partir de `backend/familhas-email/Dockerfile`; depois de mudar o código, rode `docker compose up -d --build familhas-email`. A porta 9000 do Keycloak não é publicada no host. Ela fica só na rede do Docker, em HTTP (`KC_HTTP_MANAGEMENT_SCHEME=http`), para o healthcheck funcionar sem curl na imagem.

Para parar sem perder dados: `docker compose down`. Para zerar tudo, incluindo os bancos: `docker compose down -v`.

## O que sobe automaticamente no Keycloak

Realm `familhas` importado de `infra/keycloak/data/import/familhas-realm.json` (versionado no Git). Detalhes em [ADR 0004](../docs/adr/0004-seguranca-keycloak-rotas-problem-details.md).

- Papéis: `ROLE_USER` (todo usuário recebe pelo papel padrão do realm) e `ROLE_ADMIN`.
- Clients:
  - `familhas-web`: Loja, público, PKCE S256, `http://localhost:4200`.
  - `familhas-admin`: Admin, público, PKCE S256, `http://localhost:4300`. O fluxo de login nega quem não tem `ROLE_ADMIN`.
  - `familhas-api`: só a audiência dos tokens (bearer-only, sem segredo).
- Verificação de e-mail ligada, com e-mails enviados pelo MailHog (`http://localhost:8025`).
- Política de senha: no mínimo 8 caracteres e diferente do usuário. Proteção contra força bruta ligada.
- Usuários de teste:
  - `admin.familhas` / `admin1234`
  - `user.familhas` / `user12345`

### Aplicar mudanças do realm em um Keycloak já criado

O `--import-realm` só importa quando o realm não existe. Depois de alterar o JSON, recrie o realm local. Isso apaga os usuários criados à mão nesse realm:

1. Abra `https://localhost:8443`, entre no console com `admin` / `admin`, selecione o realm `familhas` e use **Realm settings > Action > Delete**.
2. Rode `docker compose restart keycloak`. Na subida, o realm é importado de novo a partir do JSON.

### Token para testes manuais (sem frontend)

Os clients usam só authorization code + PKCE. Para testar com curl ou Postman, ligue temporariamente **Direct access grants** no `familhas-web` (console admin > Clients > familhas-web > Settings) e desligue ao terminar. Nunca faça isso em produção.

## Imagem do Keycloak

- Construída por `infra/keycloak/Dockerfile`, que adiciona o `ojdbc17.jar` (driver Oracle) e roda `kc.sh build` com `KC_DB=oracle` e `KC_HEALTH_ENABLED=true`.
- O container sobe com `start --optimized`, sem rebuild a cada start.
- Depois de mudar o Dockerfile ou o script: `docker compose up -d --build`.

## HTTPS local do Keycloak

- O container executa `infra/keycloak/scripts/start-with-keystore.sh`.
- Se `infra/keycloak/keystore/keystore.p12` não existir, o script gera um keystore self-signed para uso local. O arquivo é ignorado pelo Git.
- Porta exposta: `8443` (ou `KEYCLOAK_HTTPS_PORT` definida no `.env`).

## Mensageria (Fase 4)

Topologia e contrato em [docs/events/README.md](../docs/events/README.md). Decisões em [ADR 0005](../docs/adr/0005-mensageria-outbox-e-familhas-email.md).

| Recurso no RabbitMQ | Declarado por | Para que serve |
|---|---|---|
| exchange `familhas.events` (topic) | API e familhas-email | eventos de integração |
| exchange/fila `familhas.events.unrouted` | API | guarda o que foi publicado sem nenhuma fila ligada |
| fila `familhas-email.user-registered` | familhas-email | boas-vindas (`client.user-registered.v1`) |
| exchange `familhas.events.dlx` e fila `familhas-email.user-registered.dlq` | familhas-email | payload inválido, falha permanente ou retry esgotado |

### Validar o onboarding ponta a ponta

1. Suba a infra (`docker compose up -d --wait`) e a API (`cd backend/familhas-api && ./mvnw spring-boot:run`).
2. Crie um usuário novo no realm `familhas` com e-mail verificado (console admin > Users > Add user, com **Email verified** ligado e uma senha em Credentials).
3. Obtenha um token desse usuário (veja "Token para testes manuais") e chame `GET http://localhost:8080/api/me` com `Authorization: Bearer <token>`.
4. Em até alguns segundos o e-mail "Boas-vindas à Familhas" aparece em `http://localhost:8025`.
5. Chamar `/api/me` de novo não gera outro e-mail.

### Reprocessar

- **Mensagem na DLQ:** corrija a causa e, no painel do RabbitMQ, abra a fila `familhas-email.user-registered.dlq` e use **Move messages** para `familhas-email.user-registered`. Um `eventId` que foi para a DLQ não fica marcado como processado, então o e-mail sai normalmente.
- **Outbox `FAILED` na API** (broker fora por muito tempo): no schema da API, rode `UPDATE outbox_event SET status = 'PENDING', attempts = 0, available_at = SYSTIMESTAMP WHERE status = 'FAILED';` e faça `COMMIT`. O publisher retoma no próximo ciclo.

### Volume do Oracle criado antes da Fase 4

O usuário `FAMILHAS_EMAIL` é criado por `oracle/init/02-create-familhas-email-user.sh`, que só roda na criação do volume. Com um volume antigo, o `familhas-email` não sobe (falha de login no Oracle). Há duas opções:

- zerar tudo: `docker compose down -v` e `docker compose up -d --wait` (apaga os dados locais);
- ou criar o usuário no volume atual:

  ```bash
  docker compose exec oracle bash /container-entrypoint-initdb.d/02-create-familhas-email-user.sh
  docker compose up -d familhas-email
  ```

## Endpoints úteis para validação

- Keycloak Admin Console: `https://localhost:8443`
- OpenID configuration: `https://localhost:8443/realms/familhas/.well-known/openid-configuration`
- Token endpoint: `https://localhost:8443/realms/familhas/protocol/openid-connect/token`
- RabbitMQ: `http://localhost:15672` (guest/guest)
- MailHog: `http://localhost:8025`
- familhas-email: `http://localhost:8081/actuator/health`
- API (com `mvnw spring-boot:run` em `backend/familhas-api`): `http://localhost:8080/actuator/health`

## Observação sobre Oracle

- O Keycloak usa o mesmo Oracle, com usuário/schema dedicado (`KEYCLOAK` por padrão), criado via `infra/oracle/init/01-create-keycloak-user.sql`.
- O `familhas-email` também tem usuário/schema dedicado (`FAMILHAS_EMAIL`), criado via `infra/oracle/init/02-create-familhas-email-user.sh` com a senha de `EMAIL_DB_PASSWORD`. Ele só tem permissão para criar as próprias tabelas.
- Os scripts de `infra/oracle/init` só rodam na primeira criação do volume.
