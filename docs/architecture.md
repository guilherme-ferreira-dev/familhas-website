# Arquitetura

Monorepo da Familhas: a Loja de pacotes (Fases 1–11) e o CRM interno da Voe Familhas (Etapas 1–6 do prompt mestre). Decisões em [docs/adr](adr/). Status por módulo em [requirements-matrix.md](requirements-matrix.md).

```
                    ┌────────────── Keycloak 26 (https) ──────────────┐
                    │ realm familhas (Loja)   realm voe-familhas-crm  │
                    └──────▲───────────────────────▲──────────────────┘
                     PKCE  │                 PKCE  │   Admin API (conta de serviço)
 ┌─────────────────┐       │   ┌────────────────────┐       │
 │ familhas-web    │───────┘   │ familhas-admin     │       │
 │ Loja (Angular)  │           │ CRM (Angular, PWA) │       │
 └───────┬─────────┘           └─────────┬──────────┘       │
         │ JWT (realm familhas)          │ JWT (realm CRM)  │
         ▼                               ▼                  │
 ┌──────────────────────────────────────────────────────────┴──┐
 │ familhas-api (Spring Boot 3.5, Java 21)                     │
 │  /api/**  Loja · /api/admin/**  Admin · /api/crm/**  CRM    │
 │  multi-issuer JWT · permissões crm:<recurso>:<ação>         │
 │  storage privado (arquivos do CRM) · outbox                 │
 └──┬──────────────┬───────────────┬────────────┬──────────────┘
    │ JDBC         │ AMQP          │ SMTP       │ HTTPS (opcionais, "não configuradas")
    ▼              ▼               ▼            ▼
 Oracle 23      RabbitMQ ──► familhas-email   SerpApi · Meta Graph · IA · Stripe (Loja)
 (Flyway)                    (Spring Boot)
```

## Componentes

| Componente | Pasta | Papel |
|---|---|---|
| `familhas-api` | `backend/familhas-api` | API única da Loja, do Admin e do CRM; Flyway do schema `FAMILHAS` |
| `familhas-email` | `backend/familhas-email` | Consome eventos do RabbitMQ e manda e-mails da Loja (idempotente) |
| `familhas-web` | `frontend/familhas-web` | Loja pública |
| `familhas-admin` | `frontend/familhas-admin` | CRM interno (era o Admin da Loja; telas da Loja só para `crm-admin`) |
| Keycloak | `infra/keycloak` | Login dos dois públicos, em realms separados (ADR 0013) |
| Oracle Free 23 | `infra/oracle` | Schemas `FAMILHAS`, `FAMILHAS_EMAIL`, `KEYCLOAK` |
| Infra local | `compose.yaml`, `infra/docker-compose.yml` | Oracle, RabbitMQ, Keycloak, MailHog, familhas-email |

## Pacotes do CRM (`br.com.familhas.crm`)

| Pacote | Conteúdo | ADR |
|---|---|---|
| `crm` (raiz) | papéis, recursos, permissões, conversor de JWT | 0013 |
| `file`, `outbound`, `integration` | storage privado e links assinados, anti-SSRF, registro de integrações | 0014 |
| `clientflight`, `flightsearch`, `voucher`, `dashboard` | Viagens | 0015 |
| `marketing`, `instagram`, `itinerary`, `brand` | Marketing & Conteúdo | 0016 |
| `team`, `backup` | Administração | 0017 |
| `privacy` | LGPD: pedidos de titular e incidentes | 0018 |

## Princípios

- **Autorização na API, sempre.** Menu e guards do Angular são só experiência; cada rota `/api/crm` tem `@PreAuthorize` com permissão explícita, e a auditoria registra escritas, exportações, downloads sensíveis e restaurações.
- **Nada simulado.** Integrações sem credencial respondem 503 `INTEGRATION_NOT_CONFIGURED` depois de validar a entrada; a tela mostra o estado e oferece o caminho manual.
- **Dados pessoais mínimos e mascarados** ([lgpd.md](lgpd.md)).
- **Evolução sem perda**: Flyway só com migrations novas ([oracle-flyway.md](oracle-flyway.md)); funcionalidades da Loja preservadas.
- **Contrato primeiro**: `docs/api/*.yaml` validado no CI; os tipos do Angular são gerados dele ([api.md](api.md)).
