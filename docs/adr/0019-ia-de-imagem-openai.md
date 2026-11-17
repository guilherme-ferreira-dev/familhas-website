# ADR 0019: IA de imagem com a OpenAI (gpt-image-1)

- Status: aceito
- Data: 17/11/2026
- Complementa: [ADR 0014](0014-fundacao-do-crm.md) (D3, integrações explícitas) e [ADR 0016](0016-marketing-do-crm.md) (marketing)

## Contexto

Os posts de cotação e de cruzeiros precisam de uma foto de fundo: a cidade da hospedagem ou o navio. Até aqui
`POST /api/crm/marketing/generate-image` só validava a entrada e respondia 503, porque nenhum provedor de IA de
imagem tinha sido escolhido. A equipe enviava uma foto própria.

A agência escolheu a OpenAI, modelo `gpt-image-1`. IA de texto e de vídeo continuam sem provedor.

## Decisões

**D1. Adapter atrás de interface.** `ImageGenerator` com uma implementação, `OpenAiImageGenerator`. Ela chama
`POST /v1/images/generations` e pede uma imagem JPEG por chamada, em 1024x1536 (retrato; o canvas corta para o
formato do post). Qualidade padrão `medium`.

**D2. Configuração só por variável de ambiente.**
- `AI_IMAGE_PROVIDER=openai` e `OPENAI_API_KEY` ligam a integração.
- Provedor sem adapter, ou chave vazia, continua "não configurado" (503). A tela de Configurações diz o que falta.
- Opcionais: `OPENAI_IMAGE_MODEL`, `OPENAI_IMAGE_QUALITY` (`low`/`medium`/`high`) e `AI_IMAGE_PER_HOUR`.
- A chave vai só no cabeçalho `Authorization`. Não aparece em log, em erro nem no navegador.

**D3. A imagem vira arquivo do CRM.** O endpoint devolve um `CrmFile` da categoria `POST_IMAGE`, gravado por
`CrmFileService.store`. Passa pelas mesmas regras do upload: tipo pelos primeiros bytes, limite de tamanho,
SHA-256 e link assinado para baixar. A tela carrega a imagem por esse link (com CORS) e compõe o post no canvas.
O interceptor registra a geração na auditoria como CREATE em `crm/marketing`.

**D4. Prompt fechado no servidor.**
- Só o nome da cidade ou do navio vem do usuário, numa linha, sem aspas nem caracteres de controle.
- O prompt sempre proíbe texto, letras, logos e marca d'água. Título, preço, logo e rodapé continuam compostos no
  navegador (ADR 0016).
- O navio é **ilustrativo**: o modelo não conhece cada navio, então o pedido é "inspirado em", sem nome no casco.
  A tela avisa a equipe e sugere foto real quando o navio importa.

**D5. Custo e falhas.**
- Teto de imagens por pessoa por hora (padrão 20), contado antes de chamar a OpenAI: `AI_IMAGE_QUOTA` (429).
- Recusa da moderação: `AI_IMAGE_REFUSED` (422). Outros erros e resposta sem imagem: `AI_IMAGE_FAILED` (502).
- A chamada à OpenAI fica fora da transação do banco. Leitura de até 120 s (`familhas.openai.timeout`).
- O upload manual continua disponível em todos os casos.

## Consequências

- Só o nome da cidade ou do navio sai para a OpenAI (EUA): nenhum dado de cliente. Ver [lgpd.md](../lgpd.md).
- Cada geração custa. A qualidade e o teto por hora são o controle; o painel da OpenAI mostra o gasto.
- Trocar de provedor é escrever outra implementação de `ImageGenerator` e aceitar o novo nome no
  `IntegrationRegistry`.
- `POST /api/crm/instagram-posts/generate` continua 503: precisa também de IA de texto.

## Evidências (17/11/2026)

- `familhas-api` `./mvnw verify`: 667 testes, 0 falhas, 0 pulados.
- `OpenAiImageGeneratorTests` (contrato sem rede): corpo e cabeçalho da chamada, decodificação, recusa da
  moderação (422), chave inválida e erro 5xx (502, sem vazar a chave), resposta sem imagem ou com base64 inválido.
- `MarketingImagePromptTests` e `IntegrationRegistryTests`: prompt sem texto, nome limpo, `openai` + chave.
- `CrmMarketingIntegrationTests` (Oracle, dublê do gerador): arquivo `POST_IMAGE` criado e com link, auditoria
  CREATE, atendimento barrado (403) sem chamar a OpenAI, nome vazio (400), recusa (422) e teto por hora (429).
- Frontend: 104 testes (novo `ai-image.spec.ts`: gera, pede o link e carrega com CORS; erro sem pedir link), lint,
  formatação e build.
- Não testado com a OpenAI de verdade: falta a chave da agência. Primeiro teste manual com `OPENAI_IMAGE_QUALITY=low`.
