# ADR 0006: Provedores, busca, preço e cotação

- Status: aceito
- Data: 12/11/2026
- Fase: 5

## Contexto

A Fase 5 pede busca de voos, hotéis, carros e pacotes; um `PricingService` com taxas, margem configurável e promoção; cotação com validade; e uma camada de provedores com cache, timeout e circuit breaker (guia, seção 6 item 7).

Antes desta fase:
- o voo não tinha cidade, preço nem assentos, e as datas do seed já tinham passado;
- o preço do pacote era só um valor digitado;
- não existiam `Quote` nem regras de margem;
- a HotelDO não tinha contrato, credenciais nem documentação (guia, seção 11).

Na primeira resposta da fase, cinco perguntas ficaram em aberto. O responsável aprovou os defaults propostos, registrados abaixo como D1 a D5.

## Decisões

**D1. Margem e taxas (provisórias)**
- Margem percentual sobre o custo dos componentes: `PRICING_MARGIN_PERCENT`, padrão 10.
- Taxa de serviço por passageiro: `PRICING_SERVICE_FEE_PER_PASSENGER`, padrão 0,00, em `PRICING_SERVICE_FEE_CURRENCY` (BRL).
- São valores provisórios até o Enéas definir as regras reais. Mudar os valores não exige deploy de código, só a variável de ambiente.

**D2. Preço do pacote: vitrine e cotação**
- A busca de pacotes mostra o preço cadastrado por pessoa ("a partir de"), já com `pricePromotion` quando houver.
- A cotação recalcula pelo `PricingService` com os componentes reais (voo, hotel e carro do pacote) e o número de passageiros.
- A promoção do pacote vira um desconto percentual equivalente: `(price - pricePromotion) / price`.
- Consequência: o total da cotação pode diferir de "preço da vitrine × passageiros". O seed atual mostra isso: o pacote Maceió custa R$ 2.799,90 por pessoa na vitrine e R$ 7.830,16 para 2 pessoas na cotação. Alinhar os preços cadastrados com os componentes fica com o Enéas (Admin, Fase 10).

**D3. HotelDO**
- Nesta fase entram a interface `HotelProvider` e os adapters curado e mock.
- O `HotelDoHotelProvider` não foi implementado: sem documentação da API/XML, qualquer parser seria baseado em formato inventado.
- Desenho previsto, para quando houver contrato e homologação:
  - cliente HTTP com timeout curto;
  - parsing com Jackson XML, mapeado para `HotelOffer`;
  - offerId `hoteldo:<id da tarifa>`;
  - cache de disponibilidade e tarifas pelo `ProviderGateway` (o TTL pode ser específico);
  - circuit breaker na instância `provider-hotel-hoteldo`;
  - fallback natural, porque o catálogo curado continua respondendo na mesma busca;
  - testes com respostas XML gravadas;
  - limites de uso do contrato aplicados com rate limiter do Resilience4j.

**D4. Quem cria a cotação**
- Só o cliente autenticado: `POST /api/me/quotes`.
- O cliente vem sempre do token, nunca do corpo da requisição.
- A cotação de outro cliente responde 404, sem revelar que existe.
- As buscas são públicas.

**D5. Voo de ida e volta**
- O voo continua sendo um registro único de ida e volta.
- V9 adiciona:
  - origem e destino (FK para `cities`);
  - preço por passageiro com moeda;
  - assentos disponíveis;
  - status, para a Loja mostrar só voos ativos.
- O seed foi para 2027 e ganhou o voo Brasília → Maceió, com 4 assentos.
- Trechos independentes ficam para quando entrar um provedor real (Duffel ou Amadeus).

**Camada de provedores**
- Há um `Provider<C, O>` genérico, com `search` e `findOffer`, especializado em `FlightProvider`, `HotelProvider` e `CarRentalProvider`.
- O offerId tem o formato `<provedor>:<id>` e indica o dono da oferta.
- Adapters:
  - **curado:** banco, só itens ativos;
  - **mock:** determinístico, com o nome "simulado"; ligado só com `PROVIDERS_MOCK_ENABLED=true`, para desenvolvimento do frontend.
- O `ProviderGateway` isola cada provedor:
  - circuit breaker e timeout do Resilience4j, na instância `provider-<tipo>-<nome>`, com chamadas em virtual threads;
  - cache Caffeine das buscas bem-sucedidas (`PROVIDERS_CACHE_TTL`, padrão 2 min);
  - um provedor fora vira `unavailableProviders` na resposta, e o resto da busca continua valendo.
- `findOffer` não usa cache, porque a cotação precisa do preço atual. Provedor fora nesse caso vira 503.
- O catálogo curado não controla disponibilidade por data para hotel e carro:
  - todo item ativo conta como disponível;
  - as datas só definem noites e diárias;
  - grupo maior que a capacidade do quarto ocupa mais quartos, arredondando para cima.

**Preço**
- `Money`: `BigDecimal` com moeda ISO 4217, escala 2, `HALF_EVEN` a cada passo.
- Fórmula: `total = componentes + margem - desconto + taxa`.
  - Margem sobre os componentes.
  - Desconto sobre (componentes + margem).
  - Taxa por passageiro.
- A busca mostra custo + margem, sem taxa nem promoção, que dependem do grupo inteiro.
- Não há câmbio: itens em moedas diferentes na mesma cotação são recusados com 422. Uma cotação inteira em USD é aceita se a taxa de serviço for zero.

**Busca**
- `/api/search/flights|hotels|cars|packages`, públicas.
- A faixa de preço vale sobre o total do grupo.
- Ordenação e paginação em memória, com lista branca por tipo: `totalPrice` e mais `departureAt`, `stars` ou `seats`.
- Datas no passado são recusadas pelo dia de São Paulo, não pelo UTC.
- Limites: até 9 passageiros, 30 noites e 60 diárias.

**Cotação (V10)**
- `quotes` e `quote_items` guardam um snapshot completo: custo por item, margem, promoção, desconto, taxa e total.
- O status é `OPEN` até `expires_at` (`QUOTE_VALIDITY`, padrão 24 h) e `EXPIRED` depois.
  - A consulta por id grava o novo status.
  - A lista só calcula o status, sem gravar.
- A resposta ao cliente mostra os itens (descrição, datas, quantidade), o subtotal já com margem, o desconto, a taxa e o total. Custo por item e margem ficam só no banco, para o Admin (Fase 10).
- Erros:
  - oferta que sumiu, assentos ou lugares insuficientes, moedas diferentes: 422;
  - pacote inativo ou inexistente: 404.

## Consequências

- **Mudança incompatível no CRUD admin de voos:** `POST/PUT /api/admin/flights` passa a exigir `originCityId`, `destinationCityId`, `pricePerPassenger`, `seatsAvailable` e `status`. `/api/flights` mostra só voos ativos.
- **Preço da vitrine diferente da cotação:** o total da vitrine dos pacotes pode diferir do total da cotação (D2) até o Enéas alinhar os preços cadastrados.
- **Cache de 2 minutos:** mudanças feitas pelo Admin no catálogo curado podem levar até 2 minutos para aparecer na busca. A cotação sempre lê o preço atual.
- **Paginação em memória:** serve para o volume do catálogo curado. Com provedores externos de muitos resultados, o próximo passo é limitar por provedor e paginar no provedor.
- **Cotação não reserva assento:** a disponibilidade é checada de novo na reserva (Fase 6).
- **Sem limpeza de cotações:** cotações expiradas ficam no banco. Retenção fica para a Fase 11.
- **Evoluções só documentadas:**
  - adapter HotelDO (D3);
  - Duffel ou Amadeus para voos e Rentcars para carros (guia, seção 11);
  - disponibilidade por data no catálogo curado;
  - câmbio;
  - regras de margem por categoria ou destino.
