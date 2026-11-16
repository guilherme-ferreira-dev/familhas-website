# ADR 0018: LGPD, operação e entrega do CRM

- Status: aceito
- Data: 16/11/2026
- Etapa: 6 do prompt mestre do CRM (plano em [requirements-matrix.md](../requirements-matrix.md))

## Contexto

A Etapa 6 revisa infraestrutura, LGPD, testes, monitoramento, documentação e demonstração. O prompt pede:
- minimização, retenção configurável, exclusão ou anonimização "quando legalmente cabíveis", exportação autorizada e registro de incidentes;
- validação de identidade em pedidos de titulares e nunca apagar automaticamente dado com obrigação legal de retenção;
- PWA que não guarde documentos nem respostas autenticadas;
- ambientes segregados, limites de recursos, backup operacional de Oracle, Keycloak **e arquivos**;
- CI com SAST, dependências, segredos e imagens;
- 11 documentos de entrega.

Decisões jurídicas (base legal final, prazos de retenção, encarregado) são da agência, não do código.

## Decisões

**D1. Pedidos de titular, sempre manuais e só pelo admin (`crm:privacidade:*`).**
- `POST /api/crm/privacy/subject-report`: dados encontrados pelo CPF em voos e vouchers, para responder o pedido de acesso. O CPF vai no corpo, nunca na URL nem no log de acesso.
- `POST /api/crm/privacy/anonymize`: prévia por padrão, aplicação com `apply=true`, numa transação.
  - Nada é apagado: nome vira "Titular anonimizado"; CPF, telefone, observações e os outros passageiros do voucher saem.
  - Fica como está, com o motivo: voo que ainda não terminou (a viagem precisa dos dados) e voucher dentro do prazo legal (`PRIVACY_VOUCHER_RETENTION`, padrão 5 anos, **premissa a confirmar com o contador/jurídico**).
- Os dois exigem `identityVerified=true` e o protocolo do pedido. A auditoria (`crm/privacy`) grava o protocolo, o CPF **mascarado** e as contagens.
- Documentos e anexos enviados não são analisados automaticamente: o relatório lista voos e vouchers; arquivos ligados a eles são tratados à mão (pendência em [lgpd.md](../lgpd.md)).

**D2. Registro de incidentes (V24, `crm_incidents`).** Datas de detecção, contenção, encerramento e das comunicações à ANPD e aos titulares; gravidade; se houve dado pessoal; categorias e estimativa de titulares, **sem os dados em si**. Status `CONTIDO` exige data de contenção; `ENCERRADO`, a de encerramento. Trava otimista. Plano em [incident-response.md](../incident-response.md).

**D3. PWA.** `@angular/service-worker` só no build de produção. `ngsw-config.json` sem `dataGroups`: só os arquivos do app (JS, CSS, `index.html`, ícones, PDF.js) entram em cache. Respostas da API, links assinados, documentos e o `config.json` nunca. Teste (`pwa.spec.ts`) falha se alguém incluir JSON, `/api` ou URL externa.

**D4. Perfil `prod` que se recusa a subir inseguro.** `ProductionReadinessCheck` lista **todos** os problemas de uma vez: `STORAGE_LINK_SECRET` curto, issuers ou CORS sem https ou com localhost, Swagger ligado, `BACKUP_ENCRYPTION_KEY` fora do formato, `META_PUBLIC_BASE_URL` sem https. Integrações opcionais não bloqueiam (aparecem como "não configuradas").

**D5. Operação.**
- `mem_limit` em todos os serviços do compose.
- `infra/oracle/backup.sh` passa a gerar também o `.tar.gz` dos arquivos do CRM, com SHA-256.
- Backup lógico do CRM (ADR 0017) e backup operacional (Data Pump + arquivos) são coisas distintas e documentadas em [backup-recovery.md](../backup-recovery.md).

**D6. CI de segurança (`.github/workflows/security.yml`).**
- CodeQL (`security-extended`) para Java e TypeScript;
- gitleaks em todo o histórico, com exceções revisadas em `.gitleaks.toml`;
- Trivy em dependências, Dockerfiles e compose: falha com HIGH/CRITICAL que tenha correção, e manda o SARIF para a aba Security;
- toda segunda às 06:00 UTC e em todo PR; `security-events: write` só nos jobs que enviam SARIF; trivy-action fixada por SHA.
- Também varre a imagem do `familhas-email` e o jar da API, onde aparecem as dependências transitivas que o `pom.xml` não mostra.
- O que o Trivy achou e o que foi feito, nas duas APIs:
  - Spring Boot 3.5.11 → **3.5.16** (CVE-2026-22731 e 22733; nenhum dos dois cenários existe aqui, mas é patch da mesma linha);
  - Tomcat 10.1.55 → **10.1.62** (5 CRITICAL), Jackson 2.21.4 → **2.21.8**, amqp-client 5.25.0 → **5.37.1** e, por causa dele, Netty 4.1.135 → **4.1.137** (CRITICAL). Fixados por propriedade no `pom.xml`, a remover quando a Boot trouxer versões iguais ou mais novas;
  - **Risco aceito** em `.trivyignore`, com validade até 28/02/2027: CVE-2026-47884 e 47890 do Spring Framework 6.2.19. Correção só na 7.0.9 (Spring Boot 4, PR do Dependabot em revisão). Não se aplicam: não há `XsltView` nem fragmentos de view em SSE (o SSE dos pagamentos manda JSON). Na data de validade o CI volta a falhar e obriga a revisar.

## Consequências

- Anonimizar é irreversível. A prévia mostra o que muda e o que fica, e a confirmação é explícita.
- Prazos legais e base legal final dependem da agência: estão marcados como pendência, não como decididos.
- O service worker serve a versão antiga até o navegador baixar a nova (atualização no próximo carregamento). Em caso de erro grave, desligar é remover o `ngsw-worker.js` do deploy (o Angular então se desregistra pelo `safety-worker`).

## Evidências (16/11/2026)

- `familhas-api` `./mvnw verify`: 658 testes, 0 falhas, 0 pulados (Boot 3.5.16 e versões fixadas). `familhas-email`: 47 testes.
  - `CrmPrivacyIntegrationTests` (Oracle, 3 cenários): relatório e anonimização com voo futuro e voucher no prazo mantidos, voucher antigo anonimizado sem passageiros, auditoria só com CPF mascarado; só admin e identidade confirmada; incidentes com as regras de fase.
  - `ProductionReadinessCheckTests`: sobe o perfil `prod` com as propriedades reais; os padrões locais são recusados de uma vez.
- Frontend: 102 testes, lint, formatação, build com `ngsw.json` sem JSON nem `/api`.
- Navegador real (Chrome, Keycloak com TOTP, schema Oracle descartável, build de produção), 12 de 12 cenários:
  - menu por papel; manifest e service worker ativos;
  - botões travados sem confirmar a identidade; relatório baixado;
  - prévia mantendo viagem e voucher; anonimização do voucher antigo;
  - incidente recusado sem data de contenção e salvo depois;
  - auditoria sem CPF inteiro; cache do PWA com 69 arquivos e nenhum da API;
  - gestor barrado.
- `infra/oracle/backup.sh` na stack local: dump, `.tar.gz` dos arquivos e os dois `.sha256` conferidos.
- gitleaks no histórico da `main` e Trivy (arquivos, imagem do `familhas-email`, jar da API) com a configuração do CI: sem achados além das exceções documentadas.
