# ADR 0011: Admin (familhas-admin e /api/admin)

- Status: aceito
- Data: 13/11/2026
- Fase: 10

## Contexto

A Fase 10 pede o painel do Enéas: dashboard, clientes (abas novos, ativos e passados), reservas, pagamentos, catálogo com margens e promoções, cotações manuais e uma planilha automática de pagamentos alimentada pelos eventos da Fase 6, com exportação. Também pede auditoria das ações do admin e um teste explícito de que cliente comum recebe 403 em `/api/admin/**` (guia, seção 8).

Restrições combinadas antes de implementar:
- projeto novo em `frontend/familhas-admin`, com login Keycloak no client `familhas-admin` (PKCE) exigindo `ROLE_ADMIN`;
- consumir só `/api/admin/**`, sem um segundo backend;
- Google Sheets fica como evolução documentada.

Decisões do responsável na primeira resposta da fase:
1. Conversas e handoffs saem desta fase: dependem do chat (Fase 9), que ainda não existe.
2. Abas de clientes com as regras da D4.
3. Margem e taxa editáveis numa tabela nova, auditada; promoção continua sendo o preço promocional do pacote.
4. Cotação manual: "faça da melhor forma" (D6).
5. Estorno pelo Admin, reprocessamento de outbox `FAILED`/DLQ, alinhamento do preço de vitrine e `BookingConfirmed` ficam como pendência.

A Fase 8 rodou em paralelo. Numeração combinada: Fase 8 com V13 e ADR 0010; Fase 10 com V14 em diante e ADR 0011.

## Decisões

**D1. Três camadas para "só admin".**
- Keycloak: o client `familhas-admin` usa o fluxo `familhas admin browser`, que nega o login de quem não tem `ROLE_ADMIN` (Fase 3).
- Front: o guard lê `realm_access.roles` do access token. Sem sessão, vai para o login. Sem o papel, vai para "sem permissão". Isso é só experiência de uso.
- API: `/api/admin/**` exige `ROLE_ADMIN` no `SecurityConfig` (Fase 3). É a única camada que decide.
- `AdminSecurityTests` varre todas as rotas `/api/admin` registradas no Spring MVC e confere 401 sem token e 403 com `ROLE_USER` ou sem papel. Rotas novas entram no teste sozinhas.
- O mesmo teste exige que toda rota admin esteja em `docs/api/admin.yaml`.

**D2. Contrato `docs/api/admin.yaml`, escrito antes do código.**
- Documenta as rotas novas e as do catálogo que o Admin consome.
- O front gera os tipos dele (`npm run api:types`) e o CI falha se ficarem desatualizados.
- Os endpoints novos usam repositórios próprios (`br.com.familhas.admin.*`), com `JpaSpecificationExecutor`, para não mexer nos repositórios do domínio que a Fase 8 também altera.

**D3. Auditoria (V14, `audit_logs`).**
- Um `HandlerInterceptor` audita toda escrita (POST, PUT, PATCH, DELETE) em `/api/admin/**`. Isso inclui o CRUD do catálogo que já existia, sem mexer em cada controller.
- Grava quem fez (`sub` e `preferred_username`), a ação, o recurso, o id, o método, o caminho, o status HTTP final (inclusive 4xx) e a data.
- Num POST, o id vem do campo `id` do corpo da resposta, capturado por um `ResponseBodyAdvice`.
- Exportações são auditadas explicitamente pelo controller, com os filtros usados (só ids e enums).
- O corpo da requisição nunca é guardado (LGPD).
- A gravação usa transação própria (`REQUIRES_NEW`), depois da resposta pronta. Se a auditoria falhar, a ação já confirmada não é desfeita; fica um log de erro. Aceitamos essa perda rara em troca de não acoplar cada service à auditoria.
- A tabela só recebe inserções; a API não altera nem apaga linhas.
- A coluna é `resource_type` porque `RESOURCE` é palavra reservada no Oracle 23.

**D4. Abas de clientes.** São exclusivas entre si, nesta ordem de prioridade:
- `ACTIVE`: tem reserva `PENDING_PAYMENT` ou `CONFIRMED`.
- `NEW`: não é ativo, está `ACTIVE`, foi cadastrado há 30 dias ou menos e não tem reserva concluída.
- `PAST`: não é ativo nem novo, e tem reserva `COMPLETED` ou `CANCELLED`, ou está `INACTIVE`.

As regras viram subconsultas `EXISTS` em `bookings`, sem join na lista. Cliente antigo sem nenhuma reserva só aparece em "Todos". O índice `bookings(client_id, status)` vem da V13 (Fase 8).

**D5. Margem e taxa no banco (V15, `pricing_settings`).**
- Tabela de linha única (`id = 1`). Enquanto ninguém salva pelo Admin, ela fica vazia e valem as variáveis de ambiente (`PRICING_*`).
- O `PricingService` passa a ler as regras de um `PricingRulesProvider`. Em produção, ele é o `PricingSettingsService`, com cache de 30 s: a instância que salvou vê a mudança na hora e as outras em até 30 s.
- Cotações e reservas guardam o próprio snapshot; a mudança só afeta cálculos novos.
- Promoção continua sendo `pricePromotion` no pacote. O filtro `onPromotion=true` alimenta a aba "Promoções".

**D6. Cotação manual (V16).**
- `POST /api/admin/quotes` monta a cotação para um cliente com o mesmo cálculo da Loja: preços reconsultados no provedor, margem, promoção do pacote e taxa.
- O admin só pode dar um desconto em valor. O limite é a margem que sobra depois da promoção, então o total nunca fica abaixo de custo + taxa. Acima disso, 422 `QUOTE_BELOW_COST` com `maxManualDiscount`.
- Cliente inativo recebe 422 `CLIENT_INACTIVE`.
- A V16 guarda `origin` (`CUSTOMER` ou `ADMIN`), `manual_discount_amount` (já somado em `discount_amount`), `internal_note` (o cliente nunca vê) e `created_by`.
- O cliente vê a cotação em `/api/me/quotes` e paga pelo fluxo normal. O contrato da Loja não muda.
- Itens avulsos usam o `offerId` do catálogo curado (`curated:{id}`).

**D7. Planilha de pagamentos em tempo quase real.**
- A grade lê a tabela `payments`, que é a fonte da verdade, filtrável e exportável. Os eventos só avisam que algo mudou.
- Cada instância da API declara uma fila anônima (exclusiva, apagada ao desconectar) ligada a `payment.#` no exchange `familhas.events`. Assim todas as instâncias recebem `PaymentConfirmed` e `PaymentFailed` e avisam as próprias conexões SSE.
- Não é um segundo backend: é um listener dentro da `familhas-api`. Mensagem inválida é descartada com log, sem DLQ, porque é só um aviso.
- SSE em `GET /api/admin/payments/stream`: evento `ready`, depois `payment` com ids e tipo, sem dados pessoais, e `: ping` a cada 25 s.
- A conexão dura 5 min e o Admin reconecta com um token novo, então um token revogado não recebe avisos por muito tempo. Limite de 50 conexões por instância.
- O front lê o stream com `fetch` + `ReadableStream` e o token no header. `EventSource` não manda header, e token na URL vaza em log de proxy.
- Estorno e sessão expirada não geram evento na Fase 6. Por isso a grade também recarrega a cada 30 s. Vários eventos juntos viram uma recarga só.
- Desligável com `ADMIN_PAYMENT_FEED_ENABLED=false`; a recarga periódica continua.

**D8. Exportação XLSX e CSV em streaming.**
- `GET /api/admin/payments/export?format=xlsx|csv` usa os mesmos filtros da grade.
- Lê em lotes de 500, cada lote numa transação curta de leitura, em ordem estável (`createdAt`, `id`).
- O XLSX usa Apache POI SXSSF, com 100 linhas em memória, temporários compactados e apagados. Valores saem como número e data de verdade, nunca como fórmula.
- O CSV sai em UTF-8 com BOM, vírgula e ponto decimal. Células de texto que começam com `=`, `+`, `-`, `@`, tab ou CR recebem `'` na frente (injeção de fórmula).
- Erros de filtro respondem 400 antes do streaming começar; depois, o status já foi enviado.
- `spring.mvc.async.request-timeout` sobe para 10 min. O padrão do Tomcat (30 s) cortaria uma exportação grande.
- O front baixa pelo `HttpClient`, com o token no header, e usa só o nome do arquivo do `Content-Disposition`.

**D9. Frontend `familhas-admin`.**
- Mesma base da Loja (ADR 0009): Angular 22.2.6 standalone e zoneless, signals, angular-auth-oidc-client, Vitest, angular-eslint com regras de acessibilidade, `config.json` de runtime e porta 4300.
- O token só vai para `{apiBaseUrl}/api/admin/`. 401 leva ao login; 403 leva a "sem permissão".
- Sem build `mock` nem MSW: os testes de tela usam `HttpTestingController`, e o Admin depende da API de verdade para ter valor.
- O catálogo usa um CRUD guiado por definição de campos (`catalog-definitions.ts`) em vez de cinco telas copiadas.
- Ferramenta interna: densa, sem SEO (`noindex`), com as cores da Loja.
- O gráfico do dashboard tem uma série, colunas finas, dica no mouse e no teclado, e tabela equivalente.
- Só tema claro, como a Loja.

## Evolução documentada: Google Sheets

Não implementado nesta fase. O desenho previsto é um consumidor dos mesmos eventos `payment.*` com fila durável própria (`familhas-sheets.payment-*`), DLQ e idempotência por `eventId`, como o `familhas-email`. Ele faria upsert da linha pelo `paymentId` numa planilha via service account, com escopo só daquela planilha e credencial em segredo. Antes, é preciso decidir quais colunas podem ir para o Google (LGPD: e-mail e nome do cliente saem da infraestrutura da Familhas).

## Consequências

- Novas migrations: V14 (`audit_logs`), V15 (`pricing_settings`) e V16 (colunas de cotação manual). Todas são aditivas.
- O `PricingService` muda de construtor (`PricingRulesProvider`). `PricingProperties` implementa a interface, então os testes antigos seguem iguais.
- `ClientFilter` ganha `segment` e `TravelPackageFilter` ganha `onPromotion`. Os dois são opcionais.
- Nova dependência: `org.apache.poi:poi-ooxml` 5.4.1.
- A Loja não muda: a cotação manual chega pelo contrato existente.
- Rotas admin antigas que o Admin não usa (avaliações, escrita de cidades e categorias) ficam fora do contrato, listadas em `AdminSecurityTests`. Elas continuam protegidas e auditadas.
