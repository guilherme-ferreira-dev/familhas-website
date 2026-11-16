# ADR 0015: Viagens do CRM (Visão geral, Voos Clientes, Pesquisar Voos, Emitir Voucher e Documentos)

- Status: aceito
- Data: 16/11/2026
- Etapa: 3 do prompt mestre do CRM (plano em [requirements-matrix.md](../requirements-matrix.md))

## Contexto

O JSON funcional pede, no grupo Viagens:
- dashboard com voos próximos e gráficos (com botão de olho; sem faturas);
- Voos Clientes (`voo_flights` e `voo_travelers`) com busca rápida sem acento e em qualquer ordem, arquivados e anexos;
- Pesquisar Voos pela SerpApi, com grupos de até 7 destinos, origem fixa com CXJ, datas exatas ou flexíveis (até 3 meses, estadia de 5 noites ajustável de 1 a 30) e as 5 últimas pesquisas com "Refazer";
- Emitir Voucher (voo, hotel, transfer, passeio; extração por IA; NOME/CPF/TELEFONE/OBSERVAÇÕES manuais; A4; WhatsApp; histórico; cabeçalho da EEBPG);
- Documentos com busca, PDF.js e URLs assinadas.

A fundação ([ADR 0014](0014-fundacao-do-crm.md)) já entrega storage privado, links assinados, integrações "não configuradas" e auditoria.

## Decisões

**D1. Voos Clientes (V19: `voo_flights`, `voo_travelers`, `voo_flight_attachments`).**
- Horário local do bilhete, sem fuso (`TIMESTAMP` → `LocalDateTime`). A lista ordena pela ida: ativos do próximo ao mais distante, arquivados (histórico) do mais recente ao mais antigo.
- Busca rápida: `name_search` guarda o nome sem acento e em minúsculas. Cada palavra digitada (até 6) precisa estar no nome do **mesmo** passageiro (subquery correlacionada), em qualquer ordem. O localizador exato também acha o voo.
- Parâmetros sempre com bind e `LIKE` escapado.
- CPF opcional, só dígitos, validado pelos dígitos verificadores e **sempre mascarado** nas respostas (`***.982.247-**`). Na edição, `cpf` null mantém o guardado: a tela nunca recebe o número inteiro.
- Trava otimista (`@Version`): salvar uma versão antiga dá 409 `VERSION_CONFLICT`, também quando a corrida acontece no commit (`ObjectOptimisticLockingFailureException`, tratada no `GlobalExceptionHandler`).
- Anexos: arquivos `FLIGHT_ATTACHMENT` (até 20 por voo). Excluir o voo exclui os anexos do banco e do storage.

**D2. Pesquisar Voos (V20: `flight_search_groups`, `flight_searches`).**
- `SerpApiFlightSearchGateway`:
  - engine `google_flights`, ida e volta (`type=1`), BRL; preço = total ida + volta por adulto;
  - a chave só existe no servidor, e os logs registram trecho e datas, nunca a URL;
  - cache de 30 min por trecho e datas;
  - "sem resultados" vira lista vazia; outros erros, 502 `FLIGHT_SEARCH_FAILED`.
- Sem `SERPAPI_API_KEY`: 503 `INTEGRATION_NOT_CONFIGURED` antes de qualquer chamada. A tela avisa e mantém os grupos utilizáveis.
- Origens fixas configuráveis (`FLIGHT_SEARCH_ORIGINS`, padrão `BSB,CXJ`). Cada destino é cotado em separado.
- Modo flexível: testa datas de ida a cada 7 dias nos meses escolhidos (até 3), com a estadia pedida, e devolve o menor preço de cada data, do mais barato ao mais caro.
- Custo controlado:
  - teto de 40 consultas por pesquisa (`FLIGHT_SEARCH_TOO_BROAD`, antes de gastar);
  - 30 pesquisas por pessoa por hora (429 `FLIGHT_SEARCH_QUOTA`);
  - no máximo 4 chamadas paralelas (threads virtuais).
- Histórico: parâmetros canônicos (destinos e meses ordenados, padrões preenchidos) → SHA-256. A mesma pesquisa só renova `last_run_at`. A tela mostra as 5 mais recentes com "Refazer". Resultados não são guardados, porque o preço muda a todo momento.
- A pesquisa é consulta, não escrita: `@AuditedManually` evita um CREATE na auditoria.

**D3. Emitir Voucher (V21: `vouchers`, sequência `seq_voucher_number`).**
- Número legível `VF-000001`. Os detalhes do serviço ficam em JSON (`details`, com `IS JSON`) e são validados por tipo (`VoucherDetails.missingFor`). A tela usa a mesma lista de obrigatórios (`voucher-fields.ts`).
- NOME, CPF, TELEFONE e OBSERVAÇÕES sempre digitados. O CPF é validado e mascarado como nos voos.
- Extração por IA: `POST /extract` exige o provedor (`AI_TEXT`) e uma implementação de `VoucherExtractor`. Hoje responde 503 e a tela mostra o aviso no lugar do botão. Quando entrar, manda só o documento, nunca pede dados pessoais ao provedor e devolve os campos para revisão.
- Arquivo de origem só da categoria `VOUCHER_SOURCE` (`FILE_CATEGORY_MISMATCH`).
- Impressão A4 numa rota fora do layout (`/vouchers/:id/imprimir`), com o cabeçalho fixo da empresa (`CompanyProperties`). O CPF inteiro sai só de `GET /{id}/document`, e cada chamada grava `DOWNLOAD` na auditoria.
- WhatsApp: link `wa.me` com o texto do voucher (serviço e observações) e **sem CPF**. Nenhuma API de mensagens é usada.
- Cancelar não apaga: o histórico fica. Voucher cancelado não pode ser corrigido.

**D4. Documentos.** Tela sobre `/api/crm/files` (categoria `DOCUMENT`): busca por nome, envio, exclusão e visualização. PDF.js 6.4.299 com o worker copiado para `/pdfjs/` (mesma origem), carregado sob demanda. O link assinado de 5 min serve o PDF e as imagens. Os documentos ficam fora do backup central (regra do JSON).

**D5. Visão geral.**
- `GET /api/crm/dashboard`: voos nos próximos 7 e 30 dias, os 8 próximos voos, voos por mês nos próximos 6 meses e vouchers do mês por tipo.
- Bloco de módulo que a pessoa não lê vem null: o marketing não vê nomes de passageiros.
- Botão de olho esconde os números; a escolha fica no `localStorage` do navegador (com fallback).
- Sem faturas.

**D6. Cache no Angular.** Não há cache compartilhado entre telas: cada tela carrega por `rxResource` e recarrega depois de salvar. Isso cumpre o "mutações invalidam as chaves afetadas" sem estado global. O logout limpa a sessão do CRM.

## Consequências

- Pesquisa flexível ampla custa até 40 chamadas pagas à SerpApi. Teto, cota e cache limitam o gasto, e os valores podem ser ajustados por variável.
- Datas de voo sem fuso: o que a tela mostra é o que está no bilhete. O "próximos 7 dias" compara com o horário de São Paulo.
- Remover o CPF já guardado de um passageiro ainda não tem botão próprio: hoje a tela só troca o CPF. A API aceita `cpf: ""` para apagar.

## Evidências (16/11/2026)

- `./mvnw verify`: 623 testes, 0 falhas, 0 pulados.
  - `CrmTravelIntegrationTests` (Oracle, 8 cenários): busca sem acento no mesmo passageiro, ordenação e arquivados, CPF mascarado e mantido, 409, anexos e permissões, voucher com número, campos por tipo, documento auditado, cancelamento, 503 da IA, pesquisa com o gateway dublado e o histórico sem duplicar, painel por papel.
  - Unitários: `FlightSearchServiceTests`, `SerpApiFlightSearchGatewayTests` (MockRestServiceServer), `SearchTextAndCpfTests`, `VoucherDetailsTests`.
- Frontend: 83 testes, lint e build.
- Navegador real (Chrome headless pelo `playwright-core`), com Keycloak real, a API num schema Oracle descartável e o build do CRM — 20 cenários:
  - login PKCE e menu por papel; razão social e CNPJ na lateral;
  - voo com CPF mascarado; anexo PDF desenhado pelo PDF.js; busca "conceicao JOAO" achando "João da Conceição";
  - painel com o voo próximo e o botão de olho;
  - voucher de hotel com origem enviada e aviso de IA; WhatsApp sem CPF; A4 com o cabeçalho da EEBPG e CPF;
  - documento achado e aberto; executável `.pdf` recusado;
  - pesquisa explicando que a SerpApi não está configurada; arquivamento;
  - marketing sem Viagens e barrado na URL do voucher.
