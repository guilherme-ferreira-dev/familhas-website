# familhas-admin

Admin da Familhas (Fase 10): painel interno do Enéas. Decisões em [ADR 0011](../../docs/adr/0011-admin.md) e contrato em [docs/api/admin.yaml](../../docs/api/admin.yaml).

## Rodando

Pré-requisitos: Node 24 (`.nvmrc`), a `familhas-api` em `http://localhost:8080` e o Keycloak em `https://localhost:8443` (ver [infra/README](../../infra/README.md)).

```bash
npm ci
npm start                 # http://localhost:4300
```

Entre com `admin.familhas` / `admin1234`. Uma conta sem `ROLE_ADMIN` (ex.: `user.familhas`) é barrada pelo Keycloak e, se chegar ao Admin, vê "sem permissão"; a API responde 403 em `/api/admin/**` de qualquer forma.

`public/config.json` define a URL da API e o Keycloak (client `familhas-admin`). O mesmo build serve qualquer ambiente; só o arquivo muda no deploy. Nunca coloque segredos nele.

## Comandos

| Comando | O que faz |
|---|---|
| `npm run lint` | ESLint, incluindo regras de acessibilidade dos templates |
| `npm run format:check` | Prettier |
| `npm run test:ci` | Vitest (jsdom) |
| `npm run build` | Build de produção em `dist/` |
| `npm run api:types` | Regera `src/app/core/api/schema.d.ts` a partir de `docs/api/admin.yaml` |
| `npm run api:check` | Falha se os tipos estiverem desatualizados (CI) |

## Telas

| Rota | Tela |
|---|---|
| `/` | Dashboard: receita líquida, reservas aguardando pagamento, cotações válidas, clientes novos e receita por dia |
| `/clientes` | Abas novos, ativos, passados e todos, com busca; `/clientes/:id` com dados, reservas e pagamentos |
| `/reservas` | Filtro por status e período; `/reservas/:id` com custo, margem e tentativas de pagamento |
| `/pagamentos` | Planilha em tempo real (SSE + recarga a cada 30 s), filtros por período, status, método e parcelas, exportação XLSX/CSV |
| `/cotacoes` | Cotações da Loja e manuais; `/cotacoes/nova` monta uma cotação para um cliente |
| `/catalogo` | Voos, hotéis, quartos, carros, pacotes, promoções e margem/taxa |
| `/auditoria` | Quem criou, alterou, excluiu ou exportou o quê |

## Segurança

- Authorization Code + PKCE (S256) no Keycloak; renovação por refresh token, sem iframe.
- O token só vai para `{apiBaseUrl}/api/admin/`. Keycloak, `config.json` e outras rotas da API nunca o recebem.
- O stream da planilha usa `fetch` com o token no header (nunca na URL) e reconecta a cada 5 min com um token novo.
- A exportação baixa pelo `HttpClient` e usa só o nome do arquivo do `Content-Disposition`.
- `noindex` e `robots.txt` bloqueando tudo: ferramenta interna.
