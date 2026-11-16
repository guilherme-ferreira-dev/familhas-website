# ADR 0013: CRM interno da Voe Familhas — identidade, papéis e acesso

- Status: aceito
- Data: 16/11/2026
- Etapa: 1 do prompt mestre do CRM (diagnóstico na [matriz de requisitos](../requirements-matrix.md))

## Contexto

O repositório tem a Loja (`familhas-web`, clientes com autocadastro e pagamento Stripe) e o Admin dessa Loja (`familhas-admin`). O prompt mestre pede um CRM **interno** de uma agência (EEBPG Agência de Viagens LTDA, marca Voe Familhas), sem autocadastro, sem multiempresa e sem módulos financeiros, sobre a mesma stack (Spring Boot, Angular, Oracle, Keycloak, Flyway). O JSON funcional (gerado para Lovable) usa RLS com `is_team()` e "o primeiro cadastro vira admin"; nenhum dos dois se aplica a esta stack.

Decisões aprovadas pelo responsável em 16/11/2026:
- a Loja e o backend de pagamentos ficam intactos;
- o `familhas-admin` vira o CRM (Etapa 2), com as telas da Loja fora do menu, mas ainda funcionando;
- realm do Keycloak separado para o CRM;
- integrações de IA com estado "não configurado" até haver provedor e credenciais.

O realm `familhas` da Loja tem `registrationAllowed: true`. Qualquer pessoa cria conta nele. Ele não pode ser a porta de entrada do CRM.

## Decisões

**D1. Realm próprio: `voe-familhas-crm`.**
- Sem autocadastro, sem "lembrar de mim", recuperação de senha por e-mail ligada, política de senha `length(12) and notUsername and notEmail and upperCase(1) and digits(1) and passwordHistory(3)`.
- Força bruta: bloqueio temporário depois de 5 falhas, espera crescente de 60 s até 15 min. O Keycloak responde "usuário ou senha inválida" também para a conta bloqueada (sem enumeração).
- Sessão: access token de 5 min, ociosidade de 30 min, máximo de 10 h; refresh token rotativo de uso único (`revokeRefreshToken`, `refreshTokenMaxReuse=0`).
- Eventos de login e eventos administrativos gravados por 90 dias, sem a representação completa dos objetos (sem dados pessoais nos eventos administrativos).
- Client `familhas-crm`: público, Authorization Code + PKCE S256, sem Implicit, sem Direct Grant, redirect em `localhost:4300`, mapper de audiência `familhas-api`. Client `familhas-api` só como audiência (bearer-only).

**D2. Papéis do CRM e permissões por recurso e ação.**
- Realm roles `crm-admin`, `crm-gestor`, `crm-atendente`, `crm-marketing`. Todos são compostos com `crm-equipe`.
- Na API, cada papel vira `ROLE_CRM_<PAPEL>` e as permissões `crm:<recurso>:<ação>` (`CrmRole`, `CrmResource`, `CrmAction`). Recursos são os módulos do JSON; ações são `ler, criar, editar, excluir, publicar, exportar, restaurar`.

| Papel | Pode |
|---|---|
| ADMIN | tudo, inclusive equipe, restauração de backup e as telas da Loja |
| GESTOR | operação e marketing completos (inclusive excluir, publicar, exportar); lê equipe e auditoria; importa dados; exporta backup, não restaura |
| ATENDENTE | dashboard; voos de clientes, vouchers e roteiros (ler, criar, editar); pesquisa de voos e documentos (ler, criar). Não exclui |
| MARKETING | dashboard; posts de cotação, cruzeiros, imagens e Instagram (inclusive excluir e publicar); lê roteiros e documentos |

- Os controllers do CRM vão usar `@PreAuthorize("hasAuthority('crm:vouchers:criar')")` (method security ligada). `CrmPermissionLiteralsTests` falha se um literal não existir na matriz.
- `GET /api/crm/me` devolve papéis e permissões para o Angular montar menu e botões. É só UX: a API confere tudo de novo.

**D3. A API aceita os dois realms, cada um com as próprias authorities.**
- `JwtIssuerAuthenticationManagerResolver`: o `iss` do token (lido sem confiar) escolhe o decoder. Cada decoder valida a assinatura pelo JWKS do próprio realm, `exp`/`nbf`, o issuer exato e a audiência `familhas-api` (`SecurityConfig.jwtValidator`). Issuer desconhecido dá 401.
- O conversor da Loja continua mapeando `ROLE_*`, mas descarta `ROLE_CRM_*`: um papel com esse nome criado no realm da Loja não abre o CRM.
- O conversor do CRM só reconhece os quatro papéis da lista. `ROLE_ADMIN` criado no realm do CRM é ignorado.
- Rotas: `/api/crm/**` exige um `ROLE_CRM_*` e vem **antes** do `GET /api/** permitAll` do catálogo. `/api/admin/**` e `/actuator/**` aceitam `ROLE_ADMIN` (Loja) ou `ROLE_CRM_ADMIN`, para o admin do CRM continuar usando as telas da Loja que saem do menu. `/api/me/**` continua só da Loja: um token do CRM nunca cria cliente.
- Rate limit de `/api/crm/**` igual ao de `/api/admin/**` (equipe interna).

**D4. MFA obrigatório para administradores.**
- O fluxo `crm browser` é o browser flow do realm (vale também para o console da conta):
  - cookie de sessão **ou** formulário;
  - no formulário: usuário e senha, depois "nega quem não tem `crm-equipe`" (mensagem "Acesso restrito à equipe da Voe Familhas."), depois TOTP obrigatório para `crm-admin`.
- Sem TOTP cadastrado, o Keycloak pede o cadastro no primeiro login (`CONFIGURE_TOTP`). Remover o TOTP no console da conta só faz o próximo login pedir o cadastro de novo.

**D5. Administrador inicial por procedimento controlado.**
- "O primeiro cadastro vira admin", regra do JSON, foi descartada.
- Local: usuários de teste no JSON do realm, só para desenvolvimento.
- Outros ambientes: o realm é importado **sem** a lista `users`. O primeiro admin é criado com `infra/keycloak/scripts/provision-crm-user.sh`: senha temporária aleatória mostrada uma vez, `UPDATE_PASSWORD` e `CONFIGURE_TOTP` obrigatórios no primeiro acesso.

**D6. Infra local só em loopback.** As portas do compose (Oracle, RabbitMQ, Keycloak, MailHog, familhas-email) são publicadas em `127.0.0.1`, e não em todas as interfaces.

## Consequências

- Nenhuma migration nesta etapa: papéis e permissões vivem no Keycloak e no código. As tabelas do CRM começam na V18 (Etapa 2 em diante).
- O `familhas-admin` ainda aponta para o realm da Loja até a Etapa 2 trocar o login para `voe-familhas-crm` / `familhas-crm`.
- Dois JWKS para buscar e manter em cache. Se o Keycloak do CRM cair, só o CRM fica sem login; a Loja segue.
- Mudança no JSON do realm exige recriar o realm local (o import usa `IGNORE_EXISTING`), como já acontecia com o realm da Loja.

## Evidências (16/11/2026)

- Testes: `CrmSecurityTests` (todas as rotas `/api/crm`: anônimo 401, cliente e admin da Loja 403, sem papel 403; cadeia real issuer → decoder → papéis; issuer desconhecido 401; papel forjado no realm da Loja 403), `JwtValidationTests` (RS256 real: assinatura de outra chave, issuer da Loja, audiência errada e token vencido recusados), `CrmRoleTests`, `CrmJwtGrantedAuthoritiesConverterTests`, `StoreJwtConverterTests`, `CrmPermissionLiteralsTests`.
- Ponta a ponta com Keycloak e API locais (login PKCE real, sem frontend): atendente, gestor e marketing recebem 200 em `/api/crm/me` e 403 em `/api/admin/**` e `/actuator/metrics`; admin passa pelo cadastro de TOTP, recebe 200 nas três, código TOTP errado é recusado; usuário sem papel é barrado no login; cliente e admin da Loja recebem 403 em `/api/crm/me`; token com payload adulterado recebe 401; 5 senhas erradas bloqueiam a conta.
