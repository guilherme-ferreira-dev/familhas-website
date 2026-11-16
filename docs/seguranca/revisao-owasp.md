# Revisão de segurança (OWASP Top 10)

Revisão da Fase 11 do guia, feita em 15/11/2026 sobre a `main` depois da Fase 10 mais as mudanças da Fase 11 ([ADR 0012](../adr/0012-qualidade-final.md)). Escopo: `familhas-api`, `familhas-email`, `familhas-web`, `familhas-admin`, realm do Keycloak e infraestrutura local. Base: [OWASP Top 10 (2021)](https://owasp.org/Top10/).

Legenda: **OK** = controle implementado e coberto por teste; **Fase 11** = corrigido nesta fase; **Pendente** = risco conhecido, com dono e próximo passo.

## Resumo

| # | Categoria | Situação |
|---|---|---|
| A01 | Controle de acesso | OK |
| A02 | Falhas criptográficas | OK no código; TLS da API depende da hospedagem |
| A03 | Injeção | OK; log injection fechada na Fase 11 |
| A04 | Design inseguro | OK; rate limiting na Fase 11 |
| A05 | Configuração insegura | Fase 11 (headers, Actuator, Swagger) |
| A06 | Componentes vulneráveis | Fase 11 (Dependabot); análise Java no CI pendente |
| A07 | Identificação e autenticação | OK |
| A08 | Integridade de software e dados | OK |
| A09 | Logs e monitoramento | Fase 11 (correlationId, logs JSON, métricas) |
| A10 | SSRF | OK (sem URL vinda do usuário) |
| — | LGPD (guia, seção 6 item 9) | **Pendente**: consentimento, exportação e exclusão |

## A01: Controle de acesso

- Negação por padrão: `anyRequest().denyAll()`; escrita fora de `/api/admin` exige `ROLE_ADMIN` (`SecurityConfig`).
- `/api/admin/**` só `ROLE_ADMIN`. `AdminSecurityTests` varre **todas** as rotas admin registradas e confere 401 (anônimo) e 403 (`ROLE_USER` e token sem papel); `AdminIntegrationTests` repete com Oracle real.
- Isolamento entre clientes: toda consulta de `/api/me/**` filtra pelo cliente do token (`keycloakId`), nunca por id vindo da URL sozinho. Coberto por `MinhasViagensIntegrationTests` (HTTP com JWT, cada cliente só vê e só mexe nos próprios dados) e `QuoteIntegrationTests` (isolamento por cliente).
- Margem, custo e nota interna da cotação não saem nas respostas do cliente (DTOs separados, testado).
- Redirect depois do login só para caminho interno (sem open redirect), testado no `familhas-web`.
- O guard dos frontends é só experiência de uso; quem decide é a API.

## A02: Falhas criptográficas

- Cartão nunca passa pela Familhas: Stripe Checkout hospedado (ADR 0007). Sem PAN, CVV ou payload do Stripe no banco ou no log.
- Segredos só por variável de ambiente; `.env` no `.gitignore`; `.env.example` sem valores reais. A API recusa chave live do Stripe.
- Keycloak em HTTPS (certificado self-signed só no local). HSTS ligado na API para requisições HTTPS (1 ano, subdomínios).
- **Hospedagem:** a API roda em HTTP no local. Em produção, TLS termina no proxy ou balanceador, com `FORWARD_HEADERS_STRATEGY=native` para a API enxergar o esquema e o IP reais.

## A03: Injeção

- SQL: Spring Data com parâmetros; consultas nativas (`lockNextBatch`, retenção) com bind; ordenação só por lista branca (ADR 0003). A única JPQL montada por concatenação (`AdminDashboardService.countByStatus`) recebe o nome da entidade fixo no código, nunca dado do usuário.
- Fórmula em planilha: a exportação XLSX/CSV prefixa células que começam com `=`, `+`, `-`, `@` (ADR 0011, testado).
- **Log injection (Fase 11):** o `X-Correlation-Id` do chamador só é aceito com 8 a 64 caracteres seguros; senão vira um UUID (`CorrelationIdFilterTests`). Em JSON (`LOG_FORMAT=ecs`), cada campo é escapado pelo encoder.
- Angular escapa interpolação por padrão; não há `bypassSecurityTrust*` nem `innerHTML` com dado da API.

## A04: Design inseguro

- Máquinas de estado explícitas para reserva e pagamento; transição inválida é recusada (ADR 0007).
- Idempotência: webhook por `event.id`, consumidores por `eventId`, checkout pelo domínio, idempotency key em toda escrita no Stripe.
- Assentos bloqueados com update condicional e prazo; reconciliação agendada.
- **Rate limiting (Fase 11):** token bucket por usuário ou IP, com limite mais baixo para escrita do cliente (30/min). 429 em Problem Details com `Retry-After`. Limitação: por instância (ADR 0012, D6).

## A05: Configuração insegura

- **Headers (Fase 11):** CSP `default-src 'none'` na API, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cache-Control: no-store` (`ApiHardeningTests`).
- **Actuator (Fase 11):** antes, o health mostrava detalhes (banco, broker, disco) para qualquer um. Agora `health`/`info` são públicos só com o status; detalhes, `metrics` e `prometheus` exigem `ROLE_ADMIN`.
- **Swagger (Fase 11):** só no perfil local; fora dele precisa de `SPRINGDOC_ENABLED=true` explícito.
- Erros: nada de stack trace, mensagem interna ou SQL na resposta (`server.error.*=never`, handler genérico com 500 sem detalhe).
- CORS: só as origens de `CORS_ALLOWED_ORIGINS`, sem credenciais (Bearer, não cookie), métodos e headers listados. Origem desconhecida recebe 403 no preflight (testado).
- CSRF desligado de propósito: a API é stateless, sem cookie de sessão; o token vai no header `Authorization`.
- Credenciais padrão do compose (`guest/guest`, `admin/admin`, senhas do Oracle) servem só para o ambiente local; em outro ambiente vêm do `.env` ou do cofre de segredos.

## A06: Componentes vulneráveis e desatualizados

- Spring Boot 3.5, Angular 22, Node 24 LTS, Java 21: versões suportadas.
- **Fase 11:** Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker (`.github/dependabot.yml`).
- `npm audit --omit=dev` em 15/11/2026: 0 vulnerabilidades no `familhas-web` e no `familhas-admin`.
- **Pendente:** análise das dependências Java no CI (OWASP Dependency-Check, que precisa de chave da NVD) e varredura das imagens Docker.

## A07: Identificação e autenticação

- Keycloak como fonte da verdade (ADR 0004): Authorization Code + PKCE S256 nos dois frontends, sem implicit nem password grant.
- A API valida assinatura (JWKS), issuer, validade e **audiência** `familhas-api` (token de outro client do realm é recusado).
- Política de senha (mínimo 8, diferente do usuário), proteção contra força bruta e verificação de e-mail ligadas no realm.
- O client `familhas-admin` nega login sem `ROLE_ADMIN` no próprio Keycloak (corrigido no PR #29).
- Tokens ficam no `sessionStorage` (padrão do `angular-auth-oidc-client`): somem ao fechar a aba, mas um XSS leria o token. A defesa é a CSP dos frontends (abaixo).

## A08: Integridade de software e dados

- Webhook do Stripe validado pela `Stripe-Signature` sobre o corpo cru, com tolerância contra replay (testado com webhook forjado).
- Eventos com JSON Schema versionado e teste de contrato nos dois lados.
- `package-lock.json` e `npm ci` no CI; Maven Wrapper com versão fixa.
- Nenhum script de CDN nos frontends: todo JavaScript vem do bundle. Só as fontes vêm do Google Fonts (CSS e arquivos de fonte).

## A09: Logs e monitoramento

- **Fase 11:** `correlationId` em toda requisição e em todo log, propagado pelo outbox até o `familhas-email`; logs JSON com `LOG_FORMAT=ecs`; métricas de outbox, DLQ, 429 e retenção no Actuator/Prometheus.
- Auditoria de toda escrita e exportação do admin (`audit_logs`, só inserção, sem corpo da requisição).
- Logs sem dado pessoal: só ids (`eventId`, `correlationId`, ids de recurso). O aviso de rate limit não loga IP nem usuário.
- Falhas de autenticação (401) e acesso negado (403) saem com o `correlationId`.
- **Pendente:** alertas (Grafana ou equivalente) sobre `familhas_outbox_events{status="FAILED"} > 0`, `familhas_email_dlq_messages > 0` e taxa de 5xx, quando houver hospedagem.

## A10: SSRF

- A API só chama destinos configurados: Stripe, JWKS do Keycloak e provedores do catálogo. Nenhuma URL vem do usuário.
- `imageUrl` dos pacotes é só repassada para o navegador; a API nunca a busca.

## LGPD (guia, seção 6 item 9)

- OK: coleta mínima (nome, e-mail e telefone), logs sem dado pessoal, cartão fora da Familhas, auditoria sem corpo de requisição.
- OK (Fase 11): retenção por idade das tabelas técnicas (ADR 0012, D8).
- **Pendente:** registro de consentimento, exportação dos dados do cliente e exclusão ou anonimização a pedido. O guia pede os três e nenhuma fase os detalhou. Precisa de decisão de produto (o que fica por obrigação fiscal, como anonimizar reservas pagas) antes do código.

## CSP recomendada para os frontends (hospedagem)

Os frontends ainda não têm hospedagem definida (pendência da Fase 7). Os headers abaixo vão no servidor que entregar o build. Troque os domínios pelos reais.

```
Content-Security-Policy: default-src 'self'; script-src 'self';
  style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
  img-src 'self' data: https:; font-src 'self' https://fonts.gstatic.com;
  connect-src 'self' https://api.familhas.com.br https://auth.familhas.com.br;
  frame-src https://auth.familhas.com.br; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: camera=(), microphone=(), geolocation=()
```

- `style-src 'unsafe-inline'`: o Angular injeta estilos de componentes; dá para trocar por nonce (`ngCspNonce`) quando houver SSR.
- Google Fonts entra em `style-src` e `font-src`. Servir as fontes do próprio domínio tira esses dois domínios da CSP (e o envio do IP do visitante ao Google).
- O checkout é um redirect para o Stripe (página hospedada), então nenhum domínio do Stripe precisa entrar em `script-src` ou `frame-src`.

## Como reproduzir as verificações

```bash
cd backend/familhas-api && ./mvnw verify          # ApiHardeningTests, AdminSecurityTests, CorrelationIdFilterTests...
cd frontend/familhas-web && npm audit --omit=dev
cd frontend/familhas-admin && npm audit --omit=dev
curl -sI http://localhost:8080/api/categories      # headers de segurança e X-Correlation-Id
```
