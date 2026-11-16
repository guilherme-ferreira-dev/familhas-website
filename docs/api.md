# API

Uma API (`familhas-api`) com quatro áreas. Os contratos ficam em [docs/api](api/README.md) (OpenAPI 3.1, escritos à mão e validados no build e no CI).

| Área | Prefixo | Quem acessa | Contrato |
|---|---|---|---|
| Loja pública | `/api/catalog`, `/api/search`, ... | anônimo | `familhas-api.yaml` |
| Cliente da Loja | `/api/me/**` | token do realm `familhas` | `checkout.yaml`, `minhas-viagens.yaml` |
| Admin da Loja | `/api/admin/**` | `ROLE_ADMIN` (Loja) ou `crm-admin` | `admin.yaml` |
| CRM interno | `/api/crm/**` | token do realm `voe-familhas-crm` + permissão | `crm.yaml` |

## Convenções

- **Erros:** Problem Details (RFC 9457) com `code` estável (ex.: `VERSION_CONFLICT`, `INTEGRATION_NOT_CONFIGURED`, `LAST_ADMIN`). O Angular traduz o `code` em `api-error.ts`.
- **Concorrência:** recursos editáveis têm `version`; versão velha dá 409 `VERSION_CONFLICT`.
- **Integrações não configuradas:** 503 `INTEGRATION_NOT_CONFIGURED`, depois de validar a entrada. `GET /api/crm/integrations` lista o estado de cada uma.
- **Arquivos:** upload multipart em `/api/crm/files`; download só por link assinado de 5 min (`/api/crm/files/{id}/link`).
- **Dados sensíveis:** CPF mascarado nas respostas; o CPF de pedido de titular vai no corpo (POST), nunca na URL.
- **Correlação:** `X-Correlation-Id` em toda resposta; mande o mesmo header para seguir a requisição nos logs.
- **Rate limit:** 429 com `Retry-After`.

## Contrato primeiro

1. Altere `docs/api/crm.yaml` (ou o contrato da área).
2. `npx @redocly/cli@2.11.1 lint docs/api/crm.yaml --config docs/api/redocly.yaml`.
3. `cd frontend/familhas-admin && npm run api:types` regera `crm-schema.d.ts`; o CI roda `api:check`.
4. Implemente. `CrmOpenApiContractTests` falha se uma rota ou método `/api/crm` do código não estiver no contrato.

## Explorar

- Swagger UI só no perfil `local` (`SPRINGDOC_ENABLED=true`): `http://localhost:8080/swagger-ui.html`. Desligado e conferido no perfil `prod`.
- Coleção Postman da Loja em [docs/postman](postman/README.md).
- Token de teste do CRM: [infra/README.md](../infra/README.md#token-para-testes-manuais-sem-frontend).
