# ADR 0014: Fundação do CRM (frontend, arquivos privados, saída para a internet, integrações e auditoria)

- Status: aceito
- Data: 16/11/2026
- Etapa: 2 do prompt mestre do CRM (plano em [requirements-matrix.md](../requirements-matrix.md))

## Contexto

A Etapa 1 ([ADR 0013](0013-crm-interno-identidade-e-acesso.md)) criou o realm do CRM, os papéis e `/api/crm/**`. Os módulos das Etapas 3 a 5 precisam de uma base comum:
- tela do CRM com os grupos do JSON;
- arquivos privados (vouchers, anexos de voo, documentos, imagens e vídeos de posts);
- busca segura de URLs externas (link de orçamento, imagem, post de inspiração);
- integrações que ainda não têm credencial (IA, SerpApi, Meta);
- auditoria de tudo.

Regras do prompt que pesam aqui: não simular integrações; uploads com limite, MIME e assinatura, nome aleatório, storage privado e sem path traversal; SSRF; URLs assinadas de curta duração; auditoria de downloads sensíveis; Angular nunca como única barreira.

## Decisões

**D1. O `familhas-admin` vira o CRM.**
- Login no realm `voe-familhas-crm`, client `familhas-crm` (`public/config.json`). O `teamGuard` exige um papel do CRM; o `permissionGuard` exige o `data.permission` da rota; o `crmAdminGuard` protege as telas da Loja.
- `CrmSession` carrega `/api/crm/me` e `/api/crm/settings` uma vez por sessão e é limpa no logout.
- Menu em `layout/navigation.ts` com os quatro grupos do JSON, filtrado por `crm:<recurso>:ler`. "Equipe" usa `crm:equipe:criar` ("somente admin" no JSON). Auditoria entra em Administração para quem tem `crm:auditoria:ler`, e a API passou a aceitar essa permissão em `GET /api/admin/audit-logs`.
- Telas da Loja: fora do menu, com as mesmas rotas, mais `/loja` para o dashboard de receita. Só o `crm-admin` abre.
- Módulos sem tela usam `PendingModulePage`: dizem em que etapa chegam ou o que os bloqueia. Sem botões de mentira.
- Lateral: a marca "Voe Familhas" em texto, com a razão social e o CNPJ embaixo (vindos da API). O arquivo da logo oficial ainda não está no repositório e não foi recriado.
- 403: de quem não tem papel no CRM, leva a "sem permissão"; de quem é da equipe mas não tem a permissão da ação, só mostra a mensagem na tela.

**D2. Dados da agência numa configuração só.** `familhas.company.*` (`CompanyProperties`): razão social, marca, CNPJ, site, WhatsApp, e-mail, Instagram. Valores do JSON. O e-mail padrão é o `voefamilhas@gmail.com` (o único do JSON). O Instagram fica vazio ("a definir") porque o JSON não informa. Voucher, roteiro e posts vão ler daqui.

**D3. Integrações explícitas, nunca simuladas.** `IntegrationRegistry` responde se cada integração (IA de texto, imagem e vídeo; SerpApi; Meta) tem credencial ou provedor. As credenciais vêm só de variáveis de ambiente. `require()` lança `IntegrationNotConfiguredException` (503, `code` `INTEGRATION_NOT_CONFIGURED`), e a tela oferece o caminho manual. Provedores de IA em `none` até a escolha. `GET /api/crm/settings` mostra a situação de cada integração, sem a credencial.

**D4. Arquivos privados (`crm_files`, V18).**
- Metadados no Oracle; conteúdo em `FileStorage`. Hoje é `LocalFileStorage`, num volume privado (`STORAGE_LOCAL_ROOT`, fora de pasta servida e ignorado pelo Git em `var/`). Um adapter S3 pode entrar sem mudar os módulos.
- Chave no disco: UUID gerado pelo CRM, nunca derivado do nome. Qualquer chave que não seja UUID é recusada antes de tocar o disco, e o caminho final é conferido contra a raiz.
- Tipo pelos primeiros bytes (PDF, JPEG, PNG, WEBP, MP4). Se o tipo declarado pelo navegador contradiz o conteúdo, o arquivo é recusado (`FILE_TYPE_MISMATCH`). SVG, HTML e executáveis são recusados.
- Limites por tipo: PDF 15 MB, imagens 10 MB, MP4 100 MB. O stream é contado e cortado no limite, mesmo que o tamanho declarado minta. SHA-256 calculado na gravação.
- `FileCategory` decide a permissão (`crm:<recurso>:criar|ler|excluir`) e os tipos aceitos. Single-tenant: o arquivo é da agência; quem lê o recurso lê o arquivo, e só isso. A checagem fica no service (`CrmAccess`), porque depende do dado: trocar o id na URL (IDOR) cai na permissão da categoria do arquivo.
- Nome original só para exibição: sem pasta, sem caracteres de controle, até 200 caracteres e com a extensão do tipo real.
- Arquivo gravado e transação desfeita: o arquivo é apagado do storage. Exclusão: o arquivo sai do storage depois do commit.

**D5. Download por link assinado de curta duração.**
- `POST /api/crm/files/{id}/link` devolve `GET /api/crm/files/{id}/content?expires=&signature=`, com HMAC-SHA256 de `id\nexpires`, validade de 5 min e comparação em tempo constante.
- É a única rota do CRM sem token (para `<img>` e PDF.js). Link inválido, vencido ou de arquivo excluído recebem o mesmo 403 `FILE_LINK_INVALID`.
- A resposta leva `Content-Security-Policy: default-src 'none'; sandbox`, `nosniff` e `Cache-Control: private, no-store`.
- Cada link emitido grava `DOWNLOAD` na auditoria.
- `STORAGE_LINK_SECRET` precisa de 32 ou mais caracteres. Vazio = segredo aleatório por execução: os links caem no restart, e com mais de uma instância o segredo é obrigatório.

**D6. Saída para a internet com política anti-SSRF.**
- `OutboundUrlPolicy` aceita só https (http só com `OUTBOUND_ALLOW_HTTP=true`), portas 443/80 e URL sem usuário:senha.
- O host precisa resolver só para IPs públicos. São bloqueados: loopback, privados, link-local (metadata 169.254.169.254), CGNAT, 0/8, multicast, reservados, ULA, link-local IPv6 e IPv4 mapeado em IPv6.
- `SafeHttpFetcher` segue no máximo 3 redirects, revalidando cada um. Timeouts de 5 s (conexão) e 10 s (resposta); corpo limitado.
- Risco residual: DNS rebinding entre a checagem e a conexão. Em produção, o egress da API passa por firewall ou proxy que bloqueie as faixas internas.

**D7. Auditoria de todo o CRM.**
- O interceptor da Fase 10 cobre `/api/crm/**`, com o recurso `crm/<segmento>`.
- Permissão negada no service fica registrada como 403, não 500.
- POSTs que não são escrita e gravam a própria linha (`@AuditedManually`, ex.: link de download) não duplicam como CREATE.
- Novas ações: `DOWNLOAD`, `PUBLISH`, `RESTORE`, `IMPORT` (V18 amplia o check). Como antes, o corpo da requisição nunca é gravado (LGPD).

**D8. Contrato `docs/api/crm.yaml`.** É a fonte dos tipos do CRM no Angular (`crm-schema.d.ts`). O lint roda no CI. `CrmOpenApiContractTests` garante que toda rota `/api/crm` do código está no contrato, com o método certo, e que só o link assinado é público.

## Consequências

- Antivírus: não há. Os tipos aceitos são verificados pelo conteúdo, mas um PDF pode carregar conteúdo malicioso para leitores vulneráveis. A visualização prevista é com PDF.js, que não executa scripts do PDF. Um adapter ClamAV fica como pendência de infraestrutura (Etapa 6).
- O volume local não tem criptografia em repouso própria. Depende do disco/volume do servidor. Pendência documentada para a Etapa 6.
- Documentos ficam fora do backup lógico central (regra do JSON). O backup operacional do volume é tratado na Etapa 6.
- Cada link de download vira uma linha de auditoria. Volume pequeno para uma agência.

## Evidências (16/11/2026)

- Testes:
  - `StoredFileTypeTests`, `CrmFileRulesTests`: tipo pelos bytes, contradição de tipo, nome saneado.
  - `LocalFileStorageTests`: path traversal.
  - `FileLinkSignerTests`: adulteração, prazo e segredo.
  - `OutboundUrlPolicyTests`: 29 URLs internas ou inseguras recusadas, inclusive host público que resolve para 127.0.0.1.
  - `IntegrationRegistryTests`, `AdminAuditInterceptorTests`, `CrmOpenApiContractTests`, `CrmSecurityTests`.
  - `CrmFoundationIntegrationTests` (Oracle): V18, upload, permissões por papel, link, exclusão, busca, settings e auditoria.
- Frontend: specs de guards, sessão, menu, módulo pendente e interceptors (69 testes); lint, Prettier e build.
- Ponta a ponta com Keycloak real e a API num Oracle descartável:
  - upload pelo atendente;
  - executável com nome `.pdf` recusado (415);
  - marketing e admin da Loja barrados (403);
  - download sem token pelo link, com conteúdo idêntico e os headers de segurança;
  - link adulterado e link de arquivo excluído recusados (403);
  - atendente não exclui (403), gestor exclui (204);
  - auditoria com CREATE 201, DOWNLOAD e DELETE 403/204.
- O E2E encontrou dois defeitos na auditoria, já corrigidos e cobertos por teste: o 403 era gravado como 500, e o link gravava um CREATE extra.
