# ADR 0004: Segurança com Keycloak, rotas por área e Problem Details

- Status: aceito
- Data: 02/11/2026
- Fase: 3

## Contexto

A API aceitava qualquer token válido do realm, sem checar audiência. As rotas misturavam leitura pública e escrita administrativa no mesmo caminho, separadas só pelo método HTTP. A tabela `clients` guardava senha em texto puro, que nunca era usada para autenticar. O client `familhas-web` permitia password grant, sem PKCE. Erros saíam em JSON próprio, cada um com um formato.

## Decisões

**Rotas por área**
- `/api/**` é o catálogo público: somente leitura e somente itens com status `ACTIVE`.
  - O `status` enviado pelo cliente é ignorado, mas os outros filtros continuam valendo.
  - Item inativo responde 404.
- `/api/admin/**` tem o CRUD completo de catálogo, clientes e avaliações, e exige `ROLE_ADMIN`.
- `/api/me/**` é a área do cliente autenticado (`ROLE_USER` ou `ROLE_ADMIN`). O `clientId` vem sempre do token (`sub`), nunca do corpo da requisição.
- Qualquer outra rota é negada (`denyAll`).

**Validação do token**
- A API confere assinatura (JWKS), issuer e audiência `familhas-api`.
- Os clients `familhas-web` e `familhas-admin` incluem a audiência por meio de um audience mapper.
- Só os papéis do realm com prefixo `ROLE_` viram authorities.

**Keycloak**
- `familhas-web` e `familhas-admin` são públicos, usam authorization code com PKCE S256 e não aceitam password grant.
- `familhas-admin` tem um fluxo de login próprio que nega quem não tem `ROLE_ADMIN`, já no Keycloak.
- `familhas-api` virou bearer-only: sem segredo e sem service account. Isso tirou o segredo que estava versionado.
- O papel padrão do realm inclui `ROLE_USER`, então quem se cadastra já é cliente.
- O realm também tem verificação de e-mail (envio pelo MailHog local), política de senha de no mínimo 8 caracteres e proteção contra força bruta.

**Identidade (V7)**
- A coluna `clients.password` foi removida, porque não se guarda credencial que não é usada (LGPD).
- Entra a coluna `keycloak_id`, única, que pode ficar nula para clientes cadastrados pelo Enéas sem login.
- No primeiro acesso a `/api/me`, o `KeycloakClientSyncService` resolve o cliente:
  - procura pelo `sub`;
  - se não achar, procura pelo e-mail e vincula, desde que o e-mail esteja verificado no Keycloak e a conta local ainda não tenha login;
  - se também não achar, cria o cliente.
- O sync roda em transação própria. Dois logins simultâneos não duplicam o cliente: a constraint unique decide.
- Conta `INACTIVE` recebe 403.
- Depois do vínculo, só o Keycloak muda o e-mail. A tentativa pelo admin retorna 409.

**Erros (RFC 7807)**
- Todo erro sai como `application/problem+json`, com os campos `type`, `title`, `status`, `detail` e `instance`.
- Validação inclui também `errors`, com a lista de campos.
- 401 mantém o header `WWW-Authenticate`.
- 500 não expõe stack trace. O detalhe fica só no log.

**CORS**
- Permitido só para as origens configuradas (`CORS_ALLOWED_ORIGINS`), por padrão Loja e Admin em localhost:4200 e 4300.
- Sem credenciais, porque a autenticação é por Bearer token e não por cookie.

## Consequências

- **Mudança incompatível de rotas:** o CRUD de catálogo saiu da raiz e foi para `/api/admin/...`, e a leitura pública agora está em `/api/...`.
- **Mudança incompatível de erros:** o corpo de erro trocou `message` por `detail` (Problem Details).
- Usuários de teste do realm: `admin.familhas` / `admin1234` e `user.familhas` / `user12345`. As senhas mudaram por causa da política de 8 caracteres.
- O import do realm (`--import-realm`) só roda quando o realm não existe. Para aplicar mudanças do JSON em um Keycloak local já criado, é preciso recriar o realm (passo a passo no `infra/README.md`).
- Para testar com um token real sem frontend, é preciso ligar o password grant temporariamente no `familhas-web` pelo console admin. Isso nunca deve ser feito em produção.
