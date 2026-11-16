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
| Identidade: logo sem fundo azul e sem slogan, razão social e CNPJ abaixo da logo | `familhas.company.*`; razão social e CNPJ abaixo da marca na lateral | parcial | **Arquivo da logo oficial a receber**; Instagram a informar | 2 ✔ / pendente |
| Armazenamento privado com URLs assinadas curtas | `crm_files` (V18), `FileStorage` local, link HMAC de 5 min, tipo pelos bytes | implementado | Adapter S3 se preciso | 2 ✔ |
| Funções de servidor para scraping, proxy de imagem, PDF, IA, SerpApi, Meta | `OutboundUrlPolicy` + `SafeHttpFetcher` (anti-SSRF); `IntegrationRegistry` com 503 `INTEGRATION_NOT_CONFIGURED` | implementado na fundação | Adapters concretos com os módulos | 2 ✔ / 3–4 |
| Cache curto, mutações invalidam, logout limpa | `CrmSession` limpa no logout; signals por página | parcial | Cache por chave com invalidação junto com os módulos de dados | 2 ✔ / 3 |
| Índices em voos e vouchers; preconnect | — | ausente | Migrations com índices; preconnect no `index.html` | 3 |
| PWA instalável | Sem manifest/service worker | ausente | `@angular/service-worker` sem cache de respostas autenticadas | 6 |

## Módulos

| Módulo | Código existente | Status | Ação | Etapa |
|---|---|---|---|---|
| Visão geral (voos próximos, gráficos, ocultar valores) | Dashboard de receita da Loja; nenhuma referência a faturas | ajuste | Dashboard operacional do CRM, sem faturas, botão de olho | 3 |
| Voos Clientes (`voo_flights`, `voo_travelers`; busca sem acento e em qualquer ordem; arquivados; anexos) | `flight` é catálogo, não voo de cliente | ausente | Tabelas novas (V18+), busca normalizada, anexos no storage | 3 |
| Pesquisar Voos (SerpApi; grupos de até 7 destinos; CXJ; datas flexíveis; 5 últimas pesquisas) | Busca no catálogo interno | ausente | Adapter SerpApi (chave no servidor, "não configurado" sem chave), histórico | 3 |
| Emitir Voucher (PDF/JPG/PNG com IA; campos manuais; A4; WhatsApp; histórico; cabeçalho EEBPG) | — | ausente | Upload, extração (IA não configurada → preenchimento manual), impressão A4 | 3 |
| Documentos (busca, PDF.js, URL assinada, fora do backup) | — | ausente | Repositório no storage privado | 3 |
| Criar Posts de cotação (5 layouts, extração de PDF, regras de texto e preço, logo por composição, PNG, Instagram) | — | ausente | Editor + composição em canvas; IA e Meta via adapters | 4 |
| Posts de Cruzeiros | — | ausente | Idem, layouts "Card no oceano" e "Oportunidade" | 4 |
| Salvar Imagens ("Story Image Saver") | — | **bloqueado** | Aguarda o código ou a especificação do app original | 4 |
| Criar posts Insta (fila, STORY/FEED/REEL, carrossel, modo cidade, publicação Meta atômica) | — | ausente | Fila no Oracle, claim atômico, adapter Meta | 4 |
| Gerar Roteiro (proposta A4, seções, observações, WhatsApp) | — | ausente | Editor + impressão; IA via adapter | 4 |
| Equipe (só admin) | Papéis no Keycloak; script de provisionamento | parcial | Tela de membros e convites pela Admin API do Keycloak | 5 |
| Importar dados (adaptadores, IDs de origem, pular duplicados) | — | **bloqueado** | Aguarda amostras do formato legado | 5 |
| Backup e restauração (um arquivo, validação, sem sobrescrever edições posteriores, envio) | Só backup físico (Data Pump) | ausente | Export/import lógico com versão por registro; envio por link temporário | 5 |

## Segurança, LGPD e operação

| Item | Status | Etapa |
|---|---|---|
| OIDC Authorization Code + PKCE, sem Implicit, sem segredo no navegador | implementado (Loja, Admin, client do CRM) | 1 ✔ |
| Validação de assinatura, issuer, audiência e expiração nos dois realms | implementado | 1 ✔ |
| MFA para administradores | implementado (TOTP obrigatório para `crm-admin`) | 1 ✔ |
| Força bruta, sessão curta, refresh rotativo | implementado no realm do CRM | 1 ✔ |
| Infra local fora da rede (portas em loopback) | implementado | 1 ✔ |
| IDOR/BOLA nos recursos do CRM | a fazer em cada módulo (teste por rota) | 3–5 |
| Upload seguro e SSRF | implementado (ADR 0014); antivírus pendente | 2 ✔ / 6 |
| Auditoria de todo o CRM (escritas, exportações, downloads sensíveis, publicação Meta) | implementado para escritas e links de download; publicação Meta junto com o módulo | 2 ✔ / 4 |
| LGPD: mascaramento de CPF, retenção, exportação/anonimização, minimização para a IA | ausente | 3 / 6 |
| CI: SAST, varredura de imagens e segredos | parcial (Dependabot) | 6 |
| Docs pedidos (`architecture`, `security`, `lgpd`, `keycloak`, `oracle-flyway`, `api`, `deployment`, `backup-recovery`, `incident-response`, `testing`) | parcial (ADRs existentes) | 6 |
