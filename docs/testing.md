# Testes

Como o projeto é testado e o que cada camada garante. A tabela teste a teste da API fica no [README](../README.md#testes); o roteiro manual da Loja em [e2e-checklist.md](e2e-checklist.md).

## Comandos

| Onde | Comando | O que roda |
|---|---|---|
| `backend/familhas-api` | `./mvnw verify` | unitários (JUnit 5, Mockito), slices web, contexto inteiro, integração com Oracle e RabbitMQ (Testcontainers), contratos OpenAPI |
| `backend/familhas-email` | `./mvnw verify` | consumidor, idempotência, retry e DLQ |
| `frontend/familhas-admin` | `npm run lint && npm run format:check && npm run test:ci && npm run build && npm run api:check` | ESLint (inclui acessibilidade), Prettier, Vitest, build de produção com service worker, tipos do contrato em dia |
| `frontend/familhas-web` | idem (sem `api:check` do CRM) | Loja |
| raiz | `docker run ... zricethezav/gitleaks:v8.28.0 git /repo --config /repo/.gitleaks.toml` | segredos no histórico (o CI roda sozinho) |

Os testes com Docker são pulados se não houver Docker. Com pouca memória, pare `keycloak` e `familhas-email` antes (`docker compose stop keycloak familhas-email`) e suba depois (`docker compose up -d --wait`).

## Critérios de aceite de segurança e onde estão cobertos

| Critério do prompt | Teste |
|---|---|
| Acesso sem login | `CrmSecurityTests`, `AdminSecurityTests`: 401 em **toda** rota registrada |
| Perfil não autorizado | `CrmSecurityTests` (cliente e admin da Loja → 403 no CRM), `CrmRoleTests` (matriz), testes de integração de cada etapa (atendente, marketing e gestor barrados) |
| JWT inválido ou expirado | `JwtValidationTests`: assinatura, issuer, audiência e expiração reais |
| IDOR | integrações por papel e por categoria de arquivo; link assinado só depois da permissão (`FileLinkSignerTests`) |
| SQL injection | bind em todo JPA/JDBC; `%` e `_` digitados escapados no `LIKE` (`CommonUtilitiesTests`) |
| XSS | Angular sem `innerHTML` de dados; CSP (`ApiHardeningTests`) |
| CSRF | API stateless com bearer; CORS por lista (`ApiHardeningTests`) |
| Upload malicioso | executável renomeado para `.pdf`, MIME divergente, tamanho (`StoredFileTypeTests`, `CrmFileRulesTests`) |
| SSRF | http, file/gopher, credenciais na URL, portas, loopback, IPs privados, link-local/metadados, IPv6 interno, DNS para rede interna (`OutboundUrlPolicyTests`) |
| Concorrência | 409 de versão; 8 claims simultâneos com 1 vencedor (`CrmMarketingIntegrationTests`) |
| LGPD | CPF mascarado, auditoria sem CPF inteiro, anonimização respeitando viagem e prazo legal (`CrmPrivacyIntegrationTests`) |
| Produção segura | `ProductionReadinessCheckTests` (sobe o perfil `prod` com as propriedades reais) |
| PWA sem dados | `pwa.spec.ts` (service worker sem `dataGroups`, sem JSON nem `/api`) |

## Testes de integração do CRM (Oracle real)

| Classe | Etapa / ADR |
|---|---|
| `CrmFoundationIntegrationTests` | 2 / 0014 |
| `CrmTravelIntegrationTests` | 3 / 0015 |
| `CrmMarketingIntegrationTests` | 4 / 0016 |
| `CrmAdministrationIntegrationTests` | 5 / 0017 |
| `CrmPrivacyIntegrationTests` | 6 / 0018 |

## E2E no navegador

Feitos a cada etapa com Chrome headless (`playwright-core`), Keycloak real (com TOTP do admin), MailHog e um schema Oracle descartável (`FAMILHAS_E2E`), com a API numa porta separada e o build de produção do CRM. Os cenários e resultados estão na seção "Evidências" de cada ADR (0015 a 0018). Não ficam no CI porque dependem do Keycloak com TOTP e do Oracle completos; o roteiro de demonstração em [demo.md](demo.md) serve também de teste manual.

## CI

- `ci.yml`: as quatro aplicações em todo PR e push na `main`.
- `security.yml`: CodeQL, gitleaks e Trivy em todo PR, push na `main` e toda segunda.
