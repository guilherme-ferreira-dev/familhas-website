# familhas-admin: CRM interno Voe Familhas

CRM interno da equipe da Voe Familhas (EEBPG Agência de Viagens LTDA), construído sobre o antigo Admin da Loja (Fase 10). Decisões em [ADR 0013](../../docs/adr/0013-crm-interno-identidade-e-acesso.md) (acesso) e [ADR 0014](../../docs/adr/0014-fundacao-do-crm.md) (fundação). Contratos em [docs/api/crm.yaml](../../docs/api/crm.yaml) e [docs/api/admin.yaml](../../docs/api/admin.yaml). Plano e status por módulo em [docs/requirements-matrix.md](../../docs/requirements-matrix.md).

## Rodando

Pré-requisitos: Node 24 (`.nvmrc`), a `familhas-api` em `http://localhost:8080` e o Keycloak em `https://localhost:8443` com o realm `voe-familhas-crm` (ver [infra/README](../../infra/README.md)).

```bash
npm ci
npm start                 # http://localhost:4300
```

Entre com um usuário do realm do CRM, por exemplo `atendente.crm` / `AtendenteCrm.2026`. O `admin.crm` pede o TOTP (no primeiro acesso, o cadastro num app autenticador). Uma conta sem papel do CRM é barrada pelo Keycloak; tokens da Loja recebem 403 em `/api/crm/**`.

`public/config.json` define a URL da API e o Keycloak (realm `voe-familhas-crm`, client `familhas-crm`). O mesmo build serve qualquer ambiente; só o arquivo muda no deploy. Nunca coloque segredos nele.

## Comandos

| Comando | O que faz |
|---|---|
| `npm run lint` | ESLint, incluindo regras de acessibilidade dos templates |
| `npm run format:check` | Prettier |
| `npm run test:ci` | Vitest (jsdom) |
| `npm run build` | Build de produção em `dist/` |
| `npm run api:types` | Regera `schema.d.ts` (admin.yaml) e `crm-schema.d.ts` (crm.yaml) em `src/app/core/api` |
| `npm run api:check` | Falha se os tipos estiverem desatualizados (CI) |

## Menu

Grupos do JSON funcional. Cada item aparece só para quem tem a permissão de leitura do módulo (`crm:<recurso>:ler`, matriz no ADR 0013):

| Grupo | Telas |
|---|---|
| Geral | Visão geral (`/`), Backup e restauração (`/backup`) |
| Viagens | Voos Clientes, Pesquisar Voos, Emitir Voucher |
| Marketing & Conteúdo | Criar Posts de cotação, Posts de Cruzeiros, Salvar Imagens, Criar posts Insta, Gerar Roteiro |
| Administração | Equipe (só admin), Importar dados, Documentos, Auditoria |

A visão geral mostra o painel (voos nos próximos 7 e 30 dias, próximos voos, voos por mês, vouchers do mês, com botão de olho), os dados da agência, a situação das integrações (IA, SerpApi e Meta aparecem como "Não configurada" até haver credencial) e em que etapa chega cada módulo.

| Rota | Tela (ADR 0015) |
|---|---|
| `/voos-clientes` | Lista pela ida, busca rápida sem acento, histórico de arquivados, filtros; `/novo`, `/:id`, `/:id/editar` com passageiros (CPF mascarado) e anexos |
| `/pesquisar-voos` | Grupos de até 7 destinos, datas exatas ou flexíveis, resultados por destino, 5 últimas pesquisas com "Refazer" |
| `/vouchers` | Histórico; `/novo` com documento de origem e dados manuais; `/:id` com WhatsApp (sem CPF); `/:id/imprimir` em A4 |
| `/documentos` | Busca por nome, envio, visualização com PDF.js (worker em `/pdfjs/`) |
| `/posts-cotacao`, `/posts-cruzeiros` | Editores com prévia fixa, layouts, logo oficial composta, PNG e envio para a fila (ADR 0016) |
| `/posts-insta` | Fila; `/novo` e `/:id` com lâminas (marca e cópia limpa), Reel com MP4, ZIP, revisão e publicação na Meta |
| `/roteiros` | Lista; `/novo` e `/:id` com quatro seções e avisos do modelo mestre; `/:id/imprimir` em uma página A4 |
| `/equipe` | Membros, convite, papel, desativar/reativar e reenviar acesso (só admin; ADR 0017) |
| `/backup` | Baixar, enviar link por e-mail, backups guardados (`?baixar=<id>` vem do e-mail) e restauração com prévia |

Os módulos ainda sem tela (Salvar Imagens e Importar dados, ambos bloqueados) mostram a etapa prevista, ou o que os bloqueia; nenhum botão finge funcionar.

As telas da Loja (`/loja`, `/clientes`, `/reservas`, `/pagamentos`, `/cotacoes`, `/catalogo`) ficaram fora do menu e só abrem para o `crm-admin`.

## Segurança

- Authorization Code + PKCE (S256) no Keycloak; renovação por refresh token, sem iframe. Tokens ficam no `sessionStorage` da biblioteca OIDC, nunca no `localStorage`.
- O token só vai para `{apiBaseUrl}/api/crm/` e `{apiBaseUrl}/api/admin/`. Keycloak, `config.json`, links externos e outras rotas da API nunca o recebem.
- Menu e guards são só experiência de uso: a API confere papel e permissão em toda chamada. Um 403 de quem é da equipe (ex.: atendente tentando excluir) mostra a mensagem na tela; um 403 de quem não tem papel no CRM leva a "sem permissão".
- O logout limpa o perfil e os dados carregados (`CrmSession.clear()`).
- `noindex` e `robots.txt` bloqueando tudo: ferramenta interna.
