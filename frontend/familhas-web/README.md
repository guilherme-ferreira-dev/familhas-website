# familhas-web

Loja da Familhas: busca, detalhe de pacote, cotação, checkout (Stripe hospedado) e WhatsApp em todas as telas. Angular 22 standalone, zoneless e com signals. Decisões no [ADR 0009](../../docs/adr/0009-frontend-da-loja.md).

## Pré-requisitos

- Node 24 LTS (`.nvmrc`)

## Comandos

```bash
npm ci
npm run start:mock   # http://localhost:4200, sem backend nem Keycloak (MSW + login simulado)
npm start            # http://localhost:4200, contra a API (8080) e o Keycloak (8443)
npm run lint
npm run format:check
npm run test:ci
npm run build        # produção
npm run api:types    # gera os tipos a partir de docs/api
npm run api:check    # falha se os tipos gerados estiverem desatualizados
```

## Configuração por ambiente

`public/config.json` é lido antes do bootstrap e validado. No deploy, troque só esse arquivo:

| Campo | Exemplo local | Observação |
|---|---|---|
| `apiBaseUrl` | `http://localhost:8080` | O token só é enviado para `{apiBaseUrl}/api/` |
| `siteUrl` | `http://localhost:4200` | Canonical, Open Graph e link do pacote no WhatsApp |
| `oidc.authority` | `https://localhost:8443/realms/familhas` | Issuer do realm |
| `oidc.clientId` | `familhas-web` | Client público, Authorization Code + PKCE |
| `oidc.scope` | `openid profile email` | |
| `whatsapp.phone` | `5500000000000` | Só dígitos, com DDI. **Placeholder:** trocar pelo WhatsApp Business do Enéas. Vazio esconde o botão |
| `whatsapp.defaultMessage` | `Olá! Vim pelo site...` | Saudação sem contexto |

O arquivo é público: nunca coloque segredos nele. O build `mock` usa `mock-public/config.mock.json`.

## Rodando com o backend

1. Suba a infraestrutura e a API (README da raiz).
2. Abra `https://localhost:8443` uma vez e aceite o certificado self-signed. Sem isso, o navegador bloqueia o login.
3. `npm start` e entre com um usuário de teste do realm (`infra/README.md`).

O Keycloak já aceita `http(s)://localhost:4200` como redirect e web origin, e a API libera essa origem no CORS (`CORS_ALLOWED_ORIGINS`). Em outro domínio, ajuste os dois.

## Mocks

`npm run start:mock` liga o MSW (`src/mocks`), com dados fictícios e os mesmos contratos da API:

- O login é simulado: "Entrar" cria a sessão na hora.
- O "Stripe" volta direto para `/checkout/{id}/sucesso`, e a reserva confirma na segunda consulta, simulando o webhook.
- Cenários de erro: busca de voos com 9 passageiros responde 503; cotação com 8 passageiros responde 422.

## Estrutura

```
src/app/core      config, auth (OIDC/mock), api (tipos gerados), catálogo, checkout, erros, SEO, WhatsApp
src/app/layout    cabeçalho e rodapé
src/app/pages     home, busca, pacote, cotação, checkout, 404
src/app/shared    componentes reutilizáveis
src/mocks         handlers e dados do MSW
src/testing       helpers de teste
```
