# Matriz de requisitos do CRM interno Voe Familhas

Fonte funcional: `prompt-voe-familhas-sem-financeiro.json`, com as regras do prompt mestre (Spring Boot, Angular, Oracle, Keycloak, Flyway; uma agência, sem SaaS, sem módulos financeiros). Diagnóstico da Etapa 0 em 16/11/2026, atualizado a cada etapa.

Status: **implementado**, **parcial**, **ausente**, **bloqueado**, **ajuste** (existe, mas precisa mudar).

## Ponto de partida

O repositório tinha a Loja pública (`familhas-web`, autocadastro, cotação, reserva e pagamento Stripe) e o Admin dessa Loja (`familhas-admin`). Os módulos do JSON não existiam. O que já estava pronto e é reaproveitado é a fundação: monólito modular Spring Boot 3.5 / Java 21, Angular 22 standalone, Oracle com Flyway (V1..V17), Keycloak com PKCE, Problem Details, rate limit, headers de segurança, `correlationId`, auditoria do Admin, adapters externos com Resilience4j e backup físico do Oracle.

Decisões aprovadas (ADR 0013): Loja e pagamentos ficam intactos; `familhas-admin` vira o CRM, e as telas da Loja saem do menu; realm próprio para o CRM; IA em estado "não configurado"; arquivos num volume privado atrás de uma interface.

## Navegação e regras globais

| Requisito do JSON | Código existente | Status | Ação | Etapa |
|---|---|---|---|---|
| Acesso só da equipe, sem cadastro público | Realm `voe-familhas-crm` sem registro; `/api/crm/**` só com `ROLE_CRM_*`; Angular no realm do CRM com `teamGuard` | implementado | — | 1 ✔ / 2 ✔ |
| "Primeiro cadastro vira admin" | Substituído por provisionamento controlado (`provision-crm-user.sh`) | implementado | — | 1 ✔ |
| RLS `is_team()` | Autorização na API (URL, `@PreAuthorize` e `CrmAccess` por `crm:<recurso>:<ação>`) | implementado na fundação | Aplicar em cada módulo novo | 1 ✔ / 2 ✔ / 3+ |
| Grupos Geral, Viagens, Marketing & Conteúdo, Administração | Shell do CRM com os 4 grupos filtrados por permissão | implementado | — | 2 ✔ |
| Sem Cartões, Produtos, Finanças & Pedidos, caixa de e-mail | Telas da Loja fora do menu, só `crm-admin` pelo endereço; teste garante que o menu não tem esses módulos | implementado | — | 2 ✔ |
| Identidade: logo sem fundo azul e sem slogan, razão social e CNPJ abaixo da logo | Logo oficial enviada pelo CRM (V22) na lateral, nos posts e no A4; razão social e CNPJ abaixo | implementado | Enviar o arquivo oficial; informar o Instagram | 2 ✔ / 4 ✔ |
| Armazenamento privado com URLs assinadas curtas | `crm_files` (V18), `FileStorage` local, link HMAC de 5 min, tipo pelos bytes | implementado | Adapter S3 se preciso | 2 ✔ |
| Funções de servidor para scraping, proxy de imagem, PDF, IA, SerpApi, Meta | `OutboundUrlPolicy` + `SafeHttpFetcher` (anti-SSRF); `IntegrationRegistry` com 503 `INTEGRATION_NOT_CONFIGURED` | implementado na fundação | Adapters concretos com os módulos | 2 ✔ / 3–4 |
| Cache curto, mutações invalidam, logout limpa | Sem cache compartilhado: cada tela recarrega após salvar; `CrmSession` limpa no logout (ADR 0015, D6) | implementado | — | 2 ✔ / 3 ✔ |
| Índices em voos e vouchers; preconnect | Índices em V19 (ida, localizador, nome) e V21 (emissão, tipo, cliente) | parcial | Preconnect: a URL da API vem do `config.json` em tempo de execução; avaliar na Etapa 6 | 3 ✔ / 6 |
| PWA instalável | Manifest, ícones e `@angular/service-worker` só com os arquivos do app (sem `dataGroups`); teste impede cache de JSON ou `/api` (ADR 0018) | implementado | — | 6 ✔ |

## Módulos

| Módulo | Código existente | Status | Ação | Etapa |
|---|---|---|---|---|
| Visão geral (voos próximos, gráficos, ocultar valores) | `GET /api/crm/dashboard` + painel: voos em 7/30 dias, próximos voos, voos por mês, vouchers do mês, botão de olho; sem faturas | implementado | — | 3 ✔ |
| Voos Clientes (`voo_flights`, `voo_travelers`; busca sem acento e em qualquer ordem; arquivados; anexos) | V19; busca no mesmo passageiro; histórico; anexos; CPF mascarado; trava otimista | implementado | — | 3 ✔ |
| Pesquisar Voos (SerpApi; grupos de até 7 destinos; CXJ; datas flexíveis; 5 últimas pesquisas) | V20; adapter SerpApi testado por contrato; 503 sem chave; teto, cota e cache; histórico com "Refazer" | implementado (**precisa de `SERPAPI_API_KEY`**) | Chave da SerpApi no servidor | 3 ✔ |
| Emitir Voucher (PDF/JPG/PNG com IA; campos manuais; A4; WhatsApp; histórico; cabeçalho EEBPG) | V21; quatro tipos; origem enviada; A4 auditado; WhatsApp sem CPF; histórico e cancelamento | implementado, **extração por IA não configurada** | Provedor de IA + `VoucherExtractor` | 3 ✔ / IA pendente |
| Documentos (busca, PDF.js, URL assinada, fora do backup) | Tela sobre `/api/crm/files`, PDF.js 6.4 com worker local, link de 5 min | implementado | — | 3 ✔ |
| Criar Posts de cotação (5 layouts, extração de PDF, regras de texto e preço, logo por composição, PNG, Instagram) | 5 layouts com "Coluna lateral", regras do JSON testadas, logo oficial composta, PNG e envio para a fila (ADR 0016) | implementado; imagem da cidade por IA com a OpenAI (ADR 0019, **precisa de `OPENAI_API_KEY`**), **extração por IA não configurada** | Provedor de IA de texto | 4 ✔ / extração pendente |
| Posts de Cruzeiros | "Card no oceano" e "Oportunidade" em Feed e Story, chamada vazia, legenda, PNG e fila | implementado; imagem ilustrativa do navio por IA com a OpenAI (ADR 0019, **precisa de `OPENAI_API_KEY`**), **importação por IA não configurada** | Provedor de IA de texto | 4 ✔ / importação pendente |
| Salvar Imagens ("Story Image Saver") | — | **bloqueado** | Aguarda o código ou a especificação do app original | 4 |
| Criar posts Insta (fila, STORY/FEED/REEL, carrossel, modo cidade, publicação Meta atômica) | V22; lâminas com marca e cópia "-limpa", Reel com MP4 próprio, ZIP, revisão humana, claim atômico e UNCERTAIN sem reenvio | implementado, **geração por IA e Meta não configuradas** | Provedor de IA; `META_*` e endereço público | 4 ✔ / integrações pendentes |
| Gerar Roteiro (proposta A4, seções, observações, WhatsApp) | V22; quatro seções, avisos do modelo mestre, alertas internos fora da proposta, A4 em uma página, WhatsApp | implementado, **geração por IA não configurada** | Provedor de IA | 4 ✔ / IA pendente |
| Equipe (só admin) | Admin API do Keycloak com conta de serviço mínima; convite por e-mail do Keycloak, troca de papel, desativar/reativar, reenviar; travas de próprio usuário e último admin (ADR 0017) | implementado (**precisa de `KEYCLOAK_CRM_TEAM_SECRET`**) | — | 5 ✔ |
| Importar dados (adaptadores, IDs de origem, pular duplicados) | — | **bloqueado** | Aguarda amostras do formato legado | 5 |
| Backup e restauração (um arquivo, validação, sem sobrescrever edições posteriores, envio) | Arquivo versionado com hash; prévia e restauração sem sobrescrever; e-mail só com o link; backup guardado com AES-256-GCM por 7 dias (V23) | implementado (envio exige SMTP e `BACKUP_ENCRYPTION_KEY`) | — | 5 ✔ |

## Segurança, LGPD e operação

| Item | Status | Etapa |
|---|---|---|
| OIDC Authorization Code + PKCE, sem Implicit, sem segredo no navegador | implementado (Loja, Admin, client do CRM) | 1 ✔ |
| Validação de assinatura, issuer, audiência e expiração nos dois realms | implementado | 1 ✔ |
| MFA para administradores | implementado (TOTP obrigatório para `crm-admin`) | 1 ✔ |
| Força bruta, sessão curta, refresh rotativo | implementado no realm do CRM | 1 ✔ |
| Infra local fora da rede (portas em loopback) | implementado | 1 ✔ |
| IDOR/BOLA nos recursos do CRM | permissão por recurso em toda rota e por categoria nos arquivos; testes por papel nas Etapas 2 e 3 | 2 ✔ / 3 ✔ / 4–5 |
| Upload seguro e SSRF | implementado (ADR 0014); antivírus fora do escopo enquanto só a equipe envia arquivos (risco aceito em [security.md](security.md)) | 2 ✔ / 6 ✔ |
| Auditoria de todo o CRM (escritas, exportações, downloads sensíveis, publicação Meta) | implementado para escritas e links de download; publicação Meta junto com o módulo | 2 ✔ / 4 |
| LGPD: mascaramento de CPF, retenção, exportação/anonimização, minimização para a IA | CPF mascarado; relatório do titular e anonimização com prévia, identidade confirmada e prazo legal (`PRIVACY_VOUCHER_RETENTION`); registro de incidentes (V24); fluxos, bases legais e fornecedores em [lgpd.md](lgpd.md) (pendências organizacionais assinaladas) | 3 ✔ / 6 ✔ |
| CI: SAST, varredura de imagens e segredos | `security.yml`: CodeQL, gitleaks, Trivy (dependências, configuração, imagem do `familhas-email` e jar da API); Boot 3.5.16 e patches de Tomcat, Jackson, amqp-client e Netty; 2 CVEs do Spring aceitos até a Boot 4 | 6 ✔ |
| Docs pedidos (`architecture`, `security`, `lgpd`, `keycloak`, `oracle-flyway`, `api`, `deployment`, `backup-recovery`, `incident-response`, `testing`) | escritos, mais o roteiro [demo.md](demo.md) | 6 ✔ |
| Produção segura e operação | perfil `prod` que recusa subir inseguro; `mem_limit` no compose; backup operacional com os arquivos do CRM; RPO/RTO a definir em [backup-recovery.md](backup-recovery.md) | 6 ✔ |
