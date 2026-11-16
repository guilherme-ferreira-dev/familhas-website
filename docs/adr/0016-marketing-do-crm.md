# ADR 0016: Marketing do CRM (logo oficial, posts de cotação e cruzeiros, fila do Instagram e roteiros)

- Status: aceito
- Data: 16/11/2026
- Etapa: 4 do prompt mestre do CRM (plano em [requirements-matrix.md](../requirements-matrix.md))

## Contexto

O grupo Marketing & Conteúdo do JSON tem cinco módulos: posts de cotação, posts de cruzeiros, Salvar Imagens, posts do Instagram e roteiros. Quase todos dependem de IA (extração de PDF, imagens, textos, vídeo) e da Meta (publicação). Nenhum provedor de IA foi escolhido, e a Meta não tem credenciais. Pelo prompt, nada pode ser simulado e as ofertas precisam de revisão humana antes de serem publicadas.

## Decisões

**D1. Logo oficial (V22, `crm_brand` e categoria `BRAND_ASSET`).**
- Arquivo PNG ou WEBP, para manter a transparência (sem fundo azul e sem slogan).
- Admin e gestor trocam a logo (`crm:dashboard:criar`); toda a equipe lê.
- A logo vai para a lateral, para os posts e para o A4 do roteiro.
- Nos posts, a logo é sempre composta no canvas do navegador, nunca desenhada pela IA. Sem logo enviada, aparece a marca em texto, sem recriar o desenho.
- O link da logo fica fora da auditoria de downloads (não é dado sensível e é lido a cada sessão).

**D2. Posts de cotação e de cruzeiros: composição no navegador.**
- Regras do JSON em `quote-text.ts`, testadas:
  - datas no formato "29 de Jan a 04 de Fev";
  - valor dividido por pessoa (1 a 10, padrão 2), arredondado para cima, com "Valor por pessoa em apto duplo";
  - carro alugado no lugar do traslado em todos os modelos;
  - pagamento padrão centralizado sob o valor e "Saída de Brasília-DF" quando não há observação.
- 5 layouts de cotação (inclui "Coluna lateral") e 2 de cruzeiro ("Card no oceano" e "Oportunidade") em Feed 1080×1080 e Story 1080×1920. Não há seletor de cor de fundo.
- A chamada do cruzeiro começa vazia.
- Prévia fixa que acompanha a rolagem; download em PNG.
- "Enviar para a fila do Instagram" gera um JPEG (formato exigido pela Meta) e cria o post em rascunho, com a legenda montada por modelo fixo.

**D3. Fila do Instagram (V22, `instagram_posts` e `instagram_post_slides`).**
- Formatos: STORY (1 imagem), FEED (1 a 20 lâminas; 2 ou mais viram carrossel) e REEL (MP4 enviado, usado exatamente como veio, com capa opcional).
- Cada lâmina guarda a imagem final com a marca (logo no topo, gancho em destaque, título, 3 a 5 tópicos, rodapé "Planeje sua viagem com a Voe Familhas • WhatsApp …") e uma cópia "-limpa", usada para reescrever o texto sem gerar a imagem de novo. Cada lâmina tem a própria legenda.
- Também há ZIP (imagens e legendas, gerado no navegador sem dependência nova) e cópia da legenda com as hashtags.
- Revisão humana: editar volta o post para rascunho, e só um post marcado "pronto" pode ser publicado.
- Publicação (`InstagramPublisher`):
  - valida antes (JPEG, limite de carrossel da Meta configurável, padrão 10; legenda de até 2.200 caracteres);
  - faz um **claim atômico** com UPDATE condicional (só um pedido sai de READY/FAILED para PUBLISHING);
  - cria os contêineres em segundo plano; a Meta baixa as mídias por links assinados de 1 h no `META_PUBLIC_BASE_URL`;
  - falha antes do `media_publish` deixa o post em FAILED, e ele pode ser publicado de novo. Token vencido (código 190) gera uma mensagem própria;
  - grava `publish_attempted_at` antes do `media_publish`. A partir daí, qualquer falha leva a **UNCERTAIN, sem reenvio automático**: alguém confere no Instagram e resolve ("foi publicado" ou "não foi publicado");
  - `InstagramPublishRecovery` resolve publicações presas depois de 30 min: FAILED se nunca chegou ao `media_publish`, UNCERTAIN se chegou.
- A Meta só conta como configurada com token, id da conta do Instagram e endereço público da API.

**D4. Roteiros (V22, `itineraries`).**
- Modelo mestre com quatro seções: apresentação, dia a dia, inclusos confirmados e investimento.
- Destino pela cidade do hotel.
- O valor informado pela equipe vale sobre o do PDF; observações com até 2.000 caracteres; até 4 fotos.
- Os alertas internos nunca vão para o A4 nem para o WhatsApp.
- `ItineraryRules` avisa sobre horários, números de voo, companhias aéreas e estrelas de hotel no texto. Só avisa, não bloqueia, porque pode haver falso positivo.
- A4 em uma página com logo, telefone, Instagram e a seção INVESTIMENTO com as condições de pagamento.

**D5. IA não configurada.**
- `MarketingAiService` valida a entrada inteira e só então responde 503 `INTEGRATION_NOT_CONFIGURED`. A validação cobre arquivos da categoria certa, o link de inspiração (só instagram.com) e links de orçamento (política anti-SSRF).
- Serviços cobertos: geração de post (ideia, cidade com 7 lâminas, até 20 lâminas, 10 referências), extração do orçamento, imagem da cidade ou do navio e geração do roteiro.
- Na tela, as funções de IA aparecem como "não configurada" e o caminho manual fica disponível.
- As regras do JSON para a IA (destino pela hospedagem, não reaproveitar preço, data ou condição do post de inspiração, não citar concorrente) ficam documentadas no service, para quando houver provedor.

**D6. Salvar Imagens** continua bloqueado: falta o código ou a especificação do app Story Image Saver.

## Consequências

- Sem provedor de IA, a equipe monta as imagens com fotos próprias. A estrutura (lâminas, cópia limpa, legendas) já é a que a IA vai preencher.
- Imagens enviadas para lâminas que depois são abandonadas antes de salvar ficam órfãs no storage. Edição e exclusão de posts salvos apagam as mídias removidas.
- Publicar exige que a API seja acessível pela Meta por https (`META_PUBLIC_BASE_URL`). Localmente, a publicação fica desligada.

## Evidências (16/11/2026)

- `./mvnw verify`: 635 testes, 0 falhas, 0 pulados.
  - `CrmMarketingIntegrationTests` (Oracle, 8 cenários):
    - logo por papel;
    - carrossel publicado uma vez só e auditado;
    - falha antes do publish com nova tentativa, e falha depois dele resultando em UNCERTAIN sem reenvio, resolvida por uma pessoa;
    - 8 claims simultâneos com 1 vencedor;
    - publicações presas resolvidas;
    - regras de formato e de mídia;
    - IA com 503 depois da validação;
    - roteiro com avisos e `DAY_OUT_OF_RANGE`.
  - Unitários: `GraphMetaGatewayTests` (token nunca na URL, erro 190) e `ItineraryRulesTests`.
- Frontend: 96 testes, lint e build.
- Navegador real (Chrome headless, Keycloak real, schema Oracle descartável) — 17 cenários:
  - logo pelo gestor e na lateral do marketing;
  - post de cotação com R$ 2.490,00 em apto duplo, traslado desligado com carro alugado, textos padrão, PNG e envio para a fila com a legenda;
  - publicar desligado sem a Meta; carrossel com 2 lâminas e ZIP;
  - cruzeiro em Story 1080×1920 com chamada vazia;
  - atendente barrado nos posts;
  - roteiro com avisos de horário e voo, WhatsApp sem alertas internos e A4 em uma página com INVESTIMENTO.
