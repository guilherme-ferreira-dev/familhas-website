# Segurança

Visão consolidada. Detalhes: revisão OWASP Top 10 da Loja em [seguranca/revisao-owasp.md](seguranca/revisao-owasp.md), identidade em [keycloak.md](keycloak.md), dados pessoais em [lgpd.md](lgpd.md), incidentes em [incident-response.md](incident-response.md).

## Autenticação e autorização

- Keycloak com Authorization Code + PKCE (S256) nos dois frontends; tokens no `sessionStorage` da biblioteca OIDC.
- API como resource server multi-issuer: o issuer do token escolhe o decoder. Token da Loja recebe 403 em `/api/crm/**`; token do CRM não serve na Loja.
- CRM: papéis `crm-admin`, `crm-gestor`, `crm-atendente`, `crm-marketing` → permissões `crm:<recurso>:<ação>` (matriz em `CrmRole`, testada em `CrmRoleTests`). `CrmSecurityTests` garante 401/403 em **toda** rota registrada.
- Admin com TOTP obrigatório; bloqueio por força bruta; sem autocadastro.
- IDOR: ids são UUID e toda leitura passa pela permissão do recurso; arquivos só por link assinado (HMAC, 5 min) emitido depois da checagem.

## Entrada e saída

| Risco | Mitigação | Teste |
|---|---|---|
| SQL injection | JPA/JdbcTemplate com bind; `LIKE` escapado | `CrmTravelIntegrationTests` |
| XSS | Angular escapa por padrão; CSP restrita na API; sem `innerHTML` com dado do usuário | lint de templates |
| CSRF | API sem cookie de sessão (bearer token); CORS por lista | `ApiHardeningTests` |
| Upload malicioso | limite de tamanho, MIME pelo conteúdo e assinatura, extensão coerente, nome aleatório, storage fora da web | `StoredFileTypeTests`, `CrmFileRulesTests` |
| Path traversal | caminho gerado pelo servidor e normalizado | `LocalFileStorageTests` |
| SSRF | `OutboundUrlPolicy`: só https, DNS resolvido e IPs privados/loopback/link-local recusados, até 3 redirects revalidados um a um, timeouts | `OutboundUrlPolicyTests` |
| Injeção de fórmula | exportações CSV/XLSX neutralizam `= + - @` | `PaymentExportWriterTests` |
| Log injection | `X-Correlation-Id` validado; logs sem corpo nem dado pessoal | `CorrelationIdFilterTests` |
| Abuso | rate limit por usuário/IP; cota e teto na pesquisa de voos | `RateLimiterTests` |

## Operações críticas

Validação, permissão, transação e concorrência: trava otimista (`@Version`, 409) em voos, vouchers, posts, roteiros e incidentes; claim atômico na publicação do Instagram; restauração e anonimização com prévia e transação única; travas de "próprio usuário" e "último admin" na Equipe.

## Segredos

- Nunca no repositório: `.env.example` só com nomes; `config.json` dos frontends sem segredo.
- Perfil `prod` recusa subir com segredo faltando ou endereço inseguro (`ProductionReadinessCheck`).
- Rotação: tabela em [incident-response.md](incident-response.md#troca-de-segredos).
- CI: gitleaks em todo o histórico.

## Cadeia de suprimentos (CI)

- `ci.yml`: build, testes, lint, contratos OpenAPI.
- `security.yml`: CodeQL (Java e TypeScript, `security-extended`), gitleaks, Trivy (dependências, Dockerfiles, compose; falha com HIGH/CRITICAL corrigível), semanal e em todo PR, permissões mínimas por job.
- Dependabot semanal (majors ignorados até revisão manual).
- Versões de Tomcat, Jackson, amqp-client e Netty fixadas no `pom.xml` por segurança; exceções do Trivy em `.trivyignore`, cada uma com justificativa e data de validade ([ADR 0018](adr/0018-lgpd-operacao-e-entrega.md)).

## Riscos aceitos e pendências

- Antivírus em uploads: não há; a equipe só envia arquivos próprios e o tipo é validado pelo conteúdo. Avaliar ClamAV se clientes passarem a enviar arquivos.
- Pentest externo antes de expor o CRM na internet.
- PRs major do Dependabot (Spring Boot 4, Angular etc.) aguardando revisão. A migração para a Boot 4 também fecha os dois CVEs do Spring Framework aceitos até 28/02/2027.
