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

Todos os serviços usam `restart: unless-stopped`: voltam sozinhos quando o Docker reinicia (ou o Mac acorda) e quando um processo morre, por exemplo por falta de memória na VM do Docker Desktop. Quem foi parado com `docker compose stop` continua parado. Com a infra e os testes de integração (Testcontainers) rodando juntos, dê ao Docker Desktop pelo menos 6 GB de memória (Settings > Resources).

## Serviços, portas e healthchecks

Todas as portas são publicadas só em `127.0.0.1` (ADR 0013): nada da infra local fica acessível pela rede.

| Serviço | Porta(s) no host | Healthcheck | Persistência |
|---|---|---|---|
| Oracle Free 23 | 1521 | `healthcheck.sh` da imagem | volume `familhas_oracle_data` |
| RabbitMQ 3.13 | 5672 (AMQP), 15672 (painel) | `rabbitmq-diagnostics check_running` | volume `familhas_rabbitmq_data` |
| Keycloak 26.4 | 8443 (HTTPS) | `GET /health/ready` na porta de gerenciamento 9000 | schema `KEYCLOAK` no Oracle |
| MailHog | 1025 (SMTP), 8025 (UI) | `GET /` na UI | memória (descartável) |
| familhas-email | 8081 (`/actuator/health`) | `GET /actuator/health` = `UP` (banco e RabbitMQ; o SMTP fica de fora para não logar no Gmail a cada 15 s) | schema `FAMILHAS_EMAIL` no Oracle |

O Keycloak depende do Oracle `healthy`. O `familhas-email` depende de Oracle, RabbitMQ e MailHog `healthy`. A imagem dele é construída a partir de `backend/familhas-email/Dockerfile`; depois de mudar o código, rode `docker compose up -d --build familhas-email`. A porta 9000 do Keycloak não é publicada no host. Ela fica só na rede do Docker, em HTTP (`KC_HTTP_MANAGEMENT_SCHEME=http`), para o healthcheck funcionar sem curl na imagem.

Para parar sem perder dados: `docker compose down`. Para zerar tudo, incluindo os bancos: `docker compose down -v`.

## O que sobe automaticamente no Keycloak

Realm `familhas` importado de `infra/keycloak/data/import/familhas-realm.json` (versionado no Git). Detalhes em [ADR 0004](../docs/adr/0004-seguranca-keycloak-rotas-problem-details.md).

- Papéis: `ROLE_USER` (todo usuário recebe pelo papel padrão do realm) e `ROLE_ADMIN`.
- Clients:
  - `familhas-web`: Loja, público, PKCE S256, `http://localhost:4200`.
  - `familhas-admin`: Admin, público, PKCE S256, `http://localhost:4300`. O fluxo `familhas admin browser` primeiro identifica o usuário (sessão ou usuário e senha, no subfluxo `familhas admin identify`) e depois nega quem não tem `ROLE_ADMIN`, inclusive quem já está logado na Loja.
    - Até 15/11/2026 o fluxo tinha o passo condicional no mesmo nível das alternativas, e o Keycloak ignorava as alternativas: nenhum login no Admin passava. Um Keycloak criado antes disso guarda o fluxo antigo (o import usa `IGNORE_EXISTING`). Para corrigir, recrie o realm (abaixo) ou, no console, mova Cookie e Username Password Form para um subfluxo `REQUIRED` antes de `familhas admin role check`.
  - `familhas-api`: só a audiência dos tokens (bearer-only, sem segredo).
- Verificação de e-mail ligada, com e-mails enviados pelo MailHog (`http://localhost:8025`).
- Política de senha: no mínimo 8 caracteres e diferente do usuário. Proteção contra força bruta ligada.
- Usuários de teste:
  - `admin.familhas` / `admin1234`
  - `user.familhas` / `user12345`

## Realm do CRM interno (`voe-familhas-crm`)

Importado de `infra/keycloak/data/import/voe-familhas-crm-realm.json`. Decisões em [ADR 0013](../docs/adr/0013-crm-interno-identidade-e-acesso.md).

- Sem autocadastro. Política de senha com no mínimo 12 caracteres, maiúscula, número e histórico de 3. Bloqueio depois de 5 senhas erradas (espera crescente de 1 a 15 min).
- Papéis: `crm-admin`, `crm-gestor`, `crm-atendente`, `crm-marketing`, todos compostos com `crm-equipe`. Quem não tem nenhum deles é barrado no login.
- MFA: `crm-admin` sempre informa o TOTP. No primeiro login, o Keycloak pede o cadastro num app autenticador.
- Client `familhas-crm`: público, PKCE S256, `http://localhost:4300` (o `familhas-admin` passa a usar este client na Etapa 2). Client `familhas-api`: só audiência.
- Eventos de login e administrativos guardados por 90 dias.
- Usuários de teste (**só no ambiente local**):

| Usuário | Senha | Papel |
|---|---|---|
| `admin.crm` | `AdminCrm.2026` | `crm-admin` (pede TOTP) |
| `gestor.crm` | `GestorCrm.2026` | `crm-gestor` |
| `atendente.crm` | `AtendenteCrm.2026` | `crm-atendente` |
| `marketing.crm` | `MarketingCrm.2026` | `crm-marketing` |
| `semacesso.crm` | `SemAcesso.2026` | nenhum (deve ser barrado) |

### Arquivos do CRM

Os uploads do CRM (vouchers, anexos, documentos, imagens) ficam em `STORAGE_LOCAL_ROOT`, por padrão `backend/familhas-api/var/crm-files`, que o Git ignora. Fora do ambiente local, use um volume privado com backup próprio e defina `STORAGE_LINK_SECRET` com 32 ou mais caracteres. Detalhes em [ADR 0014](../docs/adr/0014-fundacao-do-crm.md).

### Conta de serviço da tela Equipe

O client `familhas-crm-team` (confidencial, só `view-users`, `query-users` e `manage-users` do realm do CRM) é usado pela API na tela Equipe ([ADR 0017](../docs/adr/0017-administracao-do-crm.md)). O segredo é gerado pelo Keycloak: copie em **Clients → familhas-crm-team → Credentials** e exporte `KEYCLOAK_CRM_TEAM_SECRET` antes de subir a API. O e-mail do convite sai pelo SMTP do realm (MailHog no local).

### Criar membros da equipe fora do ambiente local

Fora do ambiente local, importe o realm **sem** a lista `users`. Depois crie cada pessoa com o script:

```bash
KC_URL=https://<keycloak> KC_CACERT=<ca.pem> \
  infra/keycloak/scripts/provision-crm-user.sh <usuario> <email> <nome> <sobrenome> crm-admin
```

O script pede a senha do admin do Keycloak sem eco e gera uma senha temporária aleatória, mostrada uma única vez. No primeiro acesso, o Keycloak exige trocar a senha e, para `crm-admin`, cadastrar o TOTP. A senha temporária deve ser entregue por um canal seguro.

### Aplicar mudanças do realm em um Keycloak já criado

O `--import-realm` só importa quando o realm não existe. Depois de alterar o JSON, recrie o realm local. Isso apaga os usuários criados à mão nesse realm:

1. Abra `https://localhost:8443`, entre no console com `admin` / `admin`, selecione o realm (`familhas` ou `voe-familhas-crm`) e use **Realm settings > Action > Delete**.
2. Rode `docker compose restart keycloak`. Na subida, o realm é importado de novo a partir do JSON.

### Token para testes manuais (sem frontend)

Os clients usam só authorization code + PKCE. O Postman faz esse fluxo sozinho: use a coleção em [docs/postman](../docs/postman/README.md), sem mudar nada no realm.

Só para curl: ligue temporariamente **Direct access grants** no `familhas-web` (console admin > Clients > familhas-web > Settings) e desligue ao terminar. Nunca faça isso em produção.

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
   A API busca as chaves do Keycloak em `https://localhost:8443`, que usa certificado self-signed. Se a validação do token falhar com erro de certificado, rode a API confiando nele por meio de um truststore local (fora do repositório):

   ```bash
   keytool -exportcert -rfc -alias keycloak-local -keystore infra/keycloak/keystore/keystore.p12 -storepass changeit -file /tmp/kc.pem
   cp "$(/usr/libexec/java_home -v 21)/lib/security/cacerts" /tmp/familhas-truststore.jks && chmod u+w /tmp/familhas-truststore.jks
   keytool -importcert -noprompt -alias familhas-keycloak-local -file /tmp/kc.pem -keystore /tmp/familhas-truststore.jks -storepass changeit
   JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/tmp/familhas-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" ./mvnw spring-boot:run
   ```

   No Linux, troque `$(/usr/libexec/java_home -v 21)` pelo `JAVA_HOME` do JDK 21.
2. Crie um usuário novo no realm `familhas` com e-mail verificado (console admin > Users > Add user, com **Email verified** ligado e uma senha em Credentials).
3. Obtenha um token desse usuário (veja "Token para testes manuais") e chame `GET http://localhost:8080/api/me` com `Authorization: Bearer <token>`.
4. Em até alguns segundos o e-mail "Boas-vindas à Familhas" aparece em `http://localhost:8025`.
5. Chamar `/api/me` de novo não gera outro e-mail.

### Enviar para um e-mail real (Gmail)

Por padrão o `familhas-email` entrega no MailHog. Para mandar de verdade:

1. Na conta Google, ligue a verificação em duas etapas e gere uma **senha de app** (myaccount.google.com/apppasswords).
2. No `.env` da raiz: `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_USERNAME=<conta>`, `SMTP_PASSWORD=<senha de app, sem espaços>` e `EMAIL_FROM="Familhas <conta>"`.
3. Recrie o container (só `restart` não relê o `.env`): `docker compose up -d --build familhas-email`.
4. Confira o que o container recebeu: `docker compose exec familhas-email env | grep SMTP_HOST` deve mostrar `smtp.gmail.com`.

Para testar pelo painel do RabbitMQ (`http://localhost:15672` > Exchanges > `familhas.events` > **Publish message**):

- routing key `client.user-registered.v1`, propriedade `content_type` = `application/json`;
- payload no contrato de [docs/events](../docs/events/README.md), com **um `eventId` novo a cada envio** (o consumidor ignora `eventId` já processado, inclusive os que foram para o MailHog):

  ```json
  {"eventId":"<uuid novo>","userId":"<uuid>","email":"voce@exemplo.com","firstName":"Teste","occurredAt":"2026-11-12T15:30:00Z","schemaVersion":1}
  ```

Payload fora do contrato (sem `eventId`, `name` em vez de `firstName`, `userId` que não é UUID) vai direto para a DLQ. O log do container mostra o motivo (`Mensagem enviada para a DLQ ... motivo=...`).

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
- familhas-email: `http://localhost:8081/actuator/health` e métricas em `http://localhost:8081/actuator/prometheus` (`familhas_email_dlq_messages`, `familhas_email_dead_lettered_total`)
- API (com `mvnw spring-boot:run` em `backend/familhas-api`): `http://localhost:8080/actuator/health`
- Swagger UI da API: `http://localhost:8080/swagger-ui.html`
- Métricas da API: `http://localhost:8080/actuator/prometheus` (token com `ROLE_ADMIN`)

## Limites de memória

Cada serviço do compose tem `mem_limit` (Oracle 3 GB, Keycloak 1 GB, RabbitMQ e `familhas-email` 512 MB, MailHog 128 MB): um vazamento num serviço não derruba os outros. Depois de mudar os limites, `docker compose up -d` recria os containers (os dados ficam nos volumes).

## Backup e restore do Oracle

`infra/oracle/backup.sh` e `infra/oracle/restore.sh` (Data Pump, com modo `--verify`). O `backup.sh` também gera o `.tar.gz` dos arquivos do CRM. Passo a passo em [docs/operacao/backup-restore.md](../docs/operacao/backup-restore.md); visão geral, RPO/RTO e alertas em [docs/backup-recovery.md](../docs/backup-recovery.md).

## Observação sobre Oracle

- O Keycloak usa o mesmo Oracle, com usuário/schema dedicado (`KEYCLOAK` por padrão), criado via `infra/oracle/init/01-create-keycloak-user.sql`.
- O `familhas-email` também tem usuário/schema dedicado (`FAMILHAS_EMAIL`), criado via `infra/oracle/init/02-create-familhas-email-user.sh` com a senha de `EMAIL_DB_PASSWORD`. Ele só tem permissão para criar as próprias tabelas.
- Os scripts de `infra/oracle/init` só rodam na primeira criação do volume.
