# Keycloak

Keycloak 26.4 com banco no Oracle (schema `KEYCLOAK`), https local em `https://localhost:8443`. Imagem e subida em [infra/README.md](../infra/README.md). Decisões em [ADR 0004](adr/0004-seguranca-keycloak-rotas-problem-details.md) (Loja) e [ADR 0013](adr/0013-crm-interno-identidade-e-acesso.md) (CRM).

## Realms

| Realm | Público | Clients | Arquivo |
|---|---|---|---|
| `familhas` | clientes da Loja e admin da Loja | `familhas-web`, `familhas-admin` (legado), `familhas-api` | `infra/keycloak/data/import/familhas-realm.json` |
| `voe-familhas-crm` | equipe da agência | `familhas-crm` (público, PKCE), `familhas-api` (audiência), `familhas-crm-team` (conta de serviço) | `infra/keycloak/data/import/voe-familhas-crm-realm.json` |

## Realm do CRM

- Sem autocadastro; senha com 12+ caracteres, maiúscula, número, histórico de 3; bloqueio após 5 erros.
- Papéis `crm-admin`, `crm-gestor`, `crm-atendente`, `crm-marketing` (compostos com `crm-equipe`). Sem papel do CRM, o login é barrado.
- `crm-admin` sempre com TOTP.
- Eventos de login e administrativos guardados por 90 dias (use em incidentes).
- `familhas-crm-team`: client confidencial com conta de serviço só com `view-users`, `query-users` e `manage-users` de `realm-management`. A API usa para a tela Equipe (convites, papel, desativar). Segredo em `KEYCLOAK_CRM_TEAM_SECRET`.

## Gestão da equipe

Pela tela **Equipe** do CRM (só admin): convite por e-mail com link de 72 h (definir senha; TOTP para admin), troca de papel, desativar/reativar, reenviar acesso. O e-mail sai pelo SMTP configurado no realm (**Realm settings → Email**; MailHog no local).

Sem a tela (primeiro admin de um ambiente novo): `infra/keycloak/scripts/provision-crm-user.sh`, descrito no [infra/README.md](../infra/README.md#criar-membros-da-equipe-fora-do-ambiente-local).

## Produção

- Importar o realm **sem** a lista `users` (usuários de teste só existem no local).
- Hostname https público com certificado gerenciado; console `master` fora da internet (VPN ou IP permitido).
- Redirect URIs e web origins do `familhas-crm` com o domínio real (sem localhost).
- SMTP do realm configurado; tema e textos em pt-BR.
- Gerar o segredo do `familhas-crm-team` e passar à API.
- Mudanças no JSON depois do primeiro import não são aplicadas sozinhas: replicar no console ou recriar o realm ([infra/README.md](../infra/README.md#aplicar-mudanças-do-realm-em-um-keycloak-já-criado)).
