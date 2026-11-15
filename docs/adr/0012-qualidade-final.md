# ADR 0012: Qualidade final (documentação, observabilidade, segurança, retenção e backup)

- Status: aceito
- Data: 15/11/2026
- Fase: 11

## Contexto

A Fase 11 do guia pede: OpenAPI/Swagger por grupo (public e admin), coleção Postman, README de execução local passo a passo, convenção de branches e commits, checklist E2E local, observabilidade básica (logs estruturados com `correlationId` e métricas do Actuator), revisão de segurança (OWASP Top 10, headers, CORS, rate limiting) e backup/restore do banco documentado.

As fases anteriores deixaram para cá:
- filtro de `correlationId` nas requisições HTTP (Fase 4; o `OutboxService` já lia o MDC);
- métricas de outbox e DLQ no Actuator (Fase 4);
- retenção de `outbox_event`, `processed_event` (Fase 4), cotações expiradas (Fase 5), `stripe_webhook_events` e reservas canceladas (Fase 6).

## Decisões

**D1. OpenAPI gerado do código, em dois grupos, sem substituir os contratos.**
- springdoc-openapi 2.8 com os grupos `public` (`/api/**` menos `/api/admin/**`) e `admin` (`/api/admin/**`). Swagger UI em `/swagger-ui.html`, JSON em `/v3/api-docs/{grupo}`. Esquema `bearer-jwt` declarado nos dois.
- Os contratos escritos à mão em `docs/api/*.yaml` continuam sendo a fonte dos tipos dos frontends, validados por teste e pelo CI. O documento gerado mostra o que o código expõe e serve para explorar a API. Manter as anotações do springdoc em todos os controllers duplicaria os contratos sem ganho.
- Ligado só no perfil `local` (`SPRINGDOC_ENABLED`, padrão `true` nele e `false` fora dele). Em outro ambiente, ligar é uma decisão explícita.
- `GeneratedOpenApiGroupsTests` garante que o grupo public não tem rota admin e que o admin só tem `/api/admin/**`.

**D2. Coleção Postman versionada e testada.**
- `docs/postman/familhas-api.postman_collection.json`, com pastas Loja (sem token), Cliente (OAuth 2.0 Authorization Code + PKCE no client `familhas-web`) e Admin (`familhas-admin`). Nenhum client novo no realm e nenhum segredo na coleção: o Postman captura o redirect para `http://localhost:4200/` ou `:4300/` na própria janela de login.
- As requisições guardam ids (pacote, cotação, reserva, cliente) em variáveis da coleção, então o fluxo cotação → reserva → checkout roda em sequência.
- Um script da coleção manda um `X-Correlation-Id` novo em toda requisição.
- `PostmanCollectionTests`: toda requisição precisa bater numa rota registrada no Spring MVC, e todo query param precisa estar no contrato da rota. Rota renomeada ou param trocado quebra o build.

**D3. `correlationId` em toda requisição e em todo log.**
- `CorrelationIdFilter`, registrado antes do Spring Security: aceita o `X-Correlation-Id` do chamador só se tiver de 8 a 64 caracteres em `[A-Za-z0-9._:-]` (cabe em `outbox_event.correlation_id` e não deixa injetar linha ou JSON no log); senão gera um UUID. Põe no MDC e devolve no header da resposta, inclusive em 401, 403 e 429.
- O mesmo id vai para o outbox (já existia), para a mensagem AMQP e para os logs do `familhas-email` (já existia). O publisher do outbox também passa a logar com o id do evento.
- CORS libera o header na requisição e o expõe na resposta, junto com `Retry-After` e `Content-Disposition`.

**D4. Logs estruturados opcionais, por variável de ambiente.**
- Spring Boot 3.5 tem logs estruturados nativos. `LOG_FORMAT=ecs` (ou `logstash`) troca o console por JSON, com `correlationId`, `eventId` e o nome do serviço como campos. Vazio mantém o texto legível do desenvolvimento local, agora com `[correlationId]` em cada linha.
- Sem biblioteca nova. Nenhum dado pessoal entra no MDC (só ids).

**D5. Métricas no Actuator, com acesso restrito.**
- Exposição: `health`, `info`, `metrics` e `prometheus` (Micrometer Prometheus). `health` e `info` continuam públicos, mas o detalhe do health (banco, broker, disco) passa a sair só para `ROLE_ADMIN` (antes era `always`). `metrics` e `prometheus` exigem `ROLE_ADMIN`.
- Métricas da Familhas:

| Métrica | Serviço | O que mostra |
|---|---|---|
| `familhas.outbox.events{status=PENDING\|FAILED}` | api | Fila do outbox. `FAILED` acima de zero pede reprocessamento manual |
| `familhas.outbox.published`, `.publish.failures`, `.abandoned` | api | Publicações confirmadas, tentativas com erro e eventos que viraram `FAILED` |
| `familhas.ratelimit.rejected{policy}` | api | Respostas 429 por grupo de rota |
| `familhas.retention.deleted{table}`, `.failures{table}` | api e email | Linhas apagadas pela retenção |
| `familhas.email.dlq.messages{queue}` | email | Mensagens paradas em cada DLQ |
| `familhas.email.dead-lettered{queue,reason}` | email | Mensagens mandadas para a DLQ, por fila e tipo de falha |

- Além delas, o padrão do Spring Boot: `http.server.requests` (com histograma), JVM, pool de conexões, RabbitMQ.
- O `familhas-email` não tem autenticação HTTP. As métricas dele não têm dado pessoal, mas a porta 8081 fica só na rede interna fora do ambiente local.
- Em produção, a recomendação é uma porta de gestão interna (`management.server.port`) para o Prometheus, sem token.

**D6. Rate limiting em memória, por usuário ou IP.**
- Balde de fichas (token bucket) por par (política, chave), em Caffeine com até 100 mil chaves e expiração de 10 minutos sem uso. A chave é o `sub` do token quando há token; senão, o IP.
- Políticas (por minuto, por variável de ambiente):

| Política | Rotas | Padrão |
|---|---|---|
| `PUBLIC` | `GET /api/**` fora de `/api/me` e `/api/admin` | 300 |
| `CLIENT_READ` | `GET /api/me/**` (inclui o polling do checkout) | 300 |
| `CLIENT_WRITE` | escrita em `/api/me/**` (cotação, reserva, checkout, favoritos, avaliações) | 30 |
| `ADMIN` | `/api/admin/**` | 600 |

- Fora do limite: webhook do Stripe (o Stripe reenvia e a assinatura autentica), Actuator, OpenAPI e preflight.
- Resposta 429 em Problem Details com `code=RATE_LIMITED` e `Retry-After`. Os dois frontends mostram "aguarde alguns segundos".
- O IP é o da conexão. Atrás de proxy reverso confiável, `FORWARD_HEADERS_STRATEGY=native` faz o Tomcat usar o `X-Forwarded-For`. Sem proxy fica `none`; senão qualquer cliente trocaria de IP a cada requisição.
- Limitação aceita: o limite é por instância. Com N instâncias, o efetivo chega a N vezes o configurado. Limite global (no gateway ou com Redis) fica como evolução, quando houver hospedagem.
- Desligado nos testes (`src/test/resources/config/application.properties`), exceto em `ApiHardeningTests`, que liga com limites baixos.

**D7. Headers de segurança na API.**
- Mantidos os padrões do Spring Security: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store` e HSTS (só em HTTPS, agora com `includeSubDomains` e 1 ano).
- Novos: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` (a API só devolve JSON), `Referrer-Policy: no-referrer` e `Permissions-Policy` sem câmera, microfone, localização e pagamento. A Swagger UI fica fora da CSP restrita, porque precisa carregar script.
- Os frontends ainda não têm hospedagem definida. A CSP recomendada para eles está em [docs/seguranca/revisao-owasp.md](../seguranca/revisao-owasp.md).

**D8. Retenção por idade, em lotes.**

| Tabela | Apaga | Prazo padrão | Variável |
|---|---|---|---|
| `outbox_event` | só `PUBLISHED`, pela data de publicação | 30 dias | `RETENTION_OUTBOX_PUBLISHED` |
| `stripe_webhook_events` | registro de idempotência, pela data de recebimento | 90 dias | `RETENTION_STRIPE_WEBHOOK_EVENTS` |
| `quotes` (e itens, em cascata) | vencida e sem reserva, pela data de validade | 90 dias | `RETENTION_EXPIRED_QUOTES` |
| `processed_event` (email) | pela data de processamento | 90 dias | `EMAIL_RETENTION_PROCESSED_EVENT` |

- Job diário (03:30 na API e 03:45 no email, fuso de São Paulo), um `DELETE ... AND ROWNUM <= 500` por transação até não sobrar nada vencido. Duas instâncias ao mesmo tempo só dividem o trabalho. Falha numa tabela não impede as outras.
- `FAILED` e `PENDING` do outbox nunca saem: pedem ação.
- Por que os prazos são seguros: o Stripe reenvia webhook por até 3 dias, e o outbox e o broker reenviam em minutos ou horas. Mensagem na DLQ não tem linha em `processed_event` (o rollback a desfez), então reprocessar a DLQ depois da limpeza não duplica e-mail.
- V17 cria os índices das três limpezas da API. `processed_event` já tinha índice desde a V1 do email.
- **Reservas canceladas não são apagadas.** Uma reserva cancelada tem pagamentos, estornos e itens com snapshot de preço: é registro financeiro e fiscal, que a LGPD permite manter para cumprir obrigação legal (art. 16, I). A exclusão ou anonimização do cliente a pedido dele é outra funcionalidade, ainda não implementada (pendência LGPD do guia, seção 6 item 9).

**D9. Backup lógico com Data Pump, com verificação.**
- `infra/oracle/backup.sh`: `expdp` dos schemas `FAMILHAS`, `FAMILHAS_EMAIL` e `KEYCLOAK` num único dump consistente (`FLASHBACK_TIME`), copiado para `backups/` (ignorado pelo Git) com SHA-256. Roda com tudo no ar. A senha do `SYSTEM` é lida dentro do container e nunca aparece na linha de comando do host.
- `infra/oracle/restore.sh --verify`: restaura em schemas temporários (`*_VERIFY`), mostra contagens e apaga. Testa o backup sem tocar nos dados.
- `infra/oracle/restore.sh`: restore de verdade. Pede confirmação, para Keycloak e `familhas-email`, derruba as sessões, apaga e recria os schemas a partir do dump e sobe os serviços de novo.
- Os dois modos usam o mesmo código de apagar e importar. Validado na stack local em 15/11/2026: backup de 6,4 MB e duas restaurações seguidas em modo `--verify`, sem nenhum erro `ORA-` e com as mesmas contagens do banco real.
- Produção (Oracle gerenciado ou não) deve somar backup físico (RMAN ou o do provedor) com recuperação até um ponto no tempo. O dump lógico atende o ambiente local e a cópia entre ambientes.

**D10. Dependências monitoradas.**
- `.github/dependabot.yml`: Maven (API e email), npm (Loja e Admin), GitHub Actions e imagens Docker, semanal, agrupando atualizações menores.
- `npm audit --omit=dev` nos dois frontends em 15/11/2026: nenhuma vulnerabilidade.

## Consequências

- Uma requisição pode ser seguida da Loja até o e-mail pelo mesmo id: header da resposta, log da API, outbox, mensagem AMQP e log do `familhas-email`.
- Quem opera vê, sem abrir o banco, se o outbox está travado ou se há mensagem na DLQ.
- As tabelas que só cresciam passam a ter tamanho estável.
- Testes novos: `CorrelationIdFilterTests`, `RateLimiterTests`, `ApiHardeningTests`, `GeneratedOpenApiGroupsTests`, `PostmanCollectionTests` e `RetentionIntegrationTests` (Oracle) na API; `ObservabilityAndRetentionIntegrationTests` (Oracle e RabbitMQ) no email.
- Variáveis novas no `.env.example`: `LOG_FORMAT`, `SPRINGDOC_ENABLED`, `FORWARD_HEADERS_STRATEGY`, `RATE_LIMIT_*`, `RETENTION_*` e `EMAIL_RETENTION_*`.

## Evolução (só documentada)

- Rate limit global (gateway ou Redis) quando houver mais de uma instância.
- Tracing distribuído (Micrometer Tracing com OpenTelemetry), que trocaria o `correlationId` próprio por `traceId`.
- Painel e alertas no Grafana: `familhas_outbox_events{status="FAILED"} > 0`, `familhas_email_dlq_messages > 0`, taxa de 5xx e de 429.
- Exclusão e anonimização do cliente a pedido (LGPD) e exportação dos dados dele.
- Análise de dependências Java no CI (OWASP Dependency-Check precisa de chave da NVD) e varredura de imagens.
