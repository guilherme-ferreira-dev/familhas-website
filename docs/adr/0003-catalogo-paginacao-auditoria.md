# ADR 0003: Catálogo, paginação, auditoria e componentes de pacote

- Status: aceito
- Data: 28/10/2026
- Fase: 2

## Contexto

A Fase 2 consolida o domínio do catálogo. Os GETs devolviam listas inteiras sem filtro. As entidades não tinham colunas de auditoria. Os pacotes não tinham moeda explícita nem hotel ou carro. O guia pede relacionamentos LAZY sem N+1, migrations com seed, paginação, filtros e um modelo flexível para hotel e carro.

## Decisões

**Paginação e filtros**
- Todo GET de lista recebe `?page=` (base 0), `?size=` (padrão 20, máximo 100) e `?sort=campo,asc|desc`.
- A resposta usa o envelope `PageResponse`: `content`, `page`, `size`, `totalElements` e `totalPages`. É um formato próprio, que não depende da serialização de `Page` do Spring.
- A ordenação só aceita campos de uma lista branca por recurso. Fora dela, a resposta é 400. Isso evita 500 com campo inexistente e impede ordenar por atributos internos, como `clients.password`.
- Os filtros são opcionais, combinados com AND, e implementados com `Specification`. A busca textual (`q`) não diferencia maiúsculas e escapa `%` e `_`.
- Em pacotes, `minPrice` e `maxPrice` comparam o preço efetivo: `pricePromotion` quando existe, senão `price`.

**Auditoria**
- `created_at` e `updated_at` em todas as entidades, via `AuditableEntity` (`@CreationTimestamp` e `@UpdateTimestamp`).
- Os campos são `Instant` em UTC, mapeados como `TIMESTAMP WITH TIME ZONE` no Oracle.
- As tabelas de junção (`client_favorites`, `client_flights`) têm só `created_at`, preenchido pelo banco.

**Relacionamentos e N+1**
- Todos os `@ManyToOne` são LAZY.
- As respostas usam apenas o id das associações, que vem do proxy sem query extra.
- Coleções são carregadas em lote com `hibernate.default_batch_fetch_size=50` em vez de `@EntityGraph`. Com paginação, fetch join de coleção faria a paginação em memória.
- Testes com Hibernate Statistics fixam o número de queries das listas de clientes e pacotes.
- TravelPackage para Flight continua N:1, como o guia recomenda, porque um voo pode compor vários pacotes.

**Dinheiro**
- `BigDecimal` com coluna `currency` (ISO 4217) em pacotes, quartos e carros.
- Na criação, a moeda ausente vira BRL. No update, a moeda ausente mantém a atual.
- Filtros de preço comparam na moeda da própria oferta, sem conversão de câmbio.

**Hotel e carro**
- Entidades `Hotel`, `RoomOffer` e `CarRentalOffer`, vindas de um catálogo curado que o Enéas cadastra no Admin.
- Entram no pacote por `travel_package_components`, uma linha por componente:
  - `HOTEL_STAY` aponta para `room_offer_id`, e `quantity` é o número de noites;
  - `CAR_RENTAL` aponta para `car_rental_offer_id`, e `quantity` é o número de dias.
- Um CHECK no banco garante exatamente uma referência coerente com o tipo.
- No update, `components` ausente mantém os atuais e lista vazia remove todos.
- O preço do pacote continua manual. A soma dos componentes fica para o `PricingService` da Fase 5.

**Exclusão**
- `DELETE` faz `flush` dentro do service, para que a violação de FK apareça na hora.
- A violação vira 409 com mensagem genérica, sem detalhes do SQL.
- Componentes de pacote são removidos em cascata.

**Migrations**
- `V5` adiciona auditoria, moeda e índices nas FKs, já que o Oracle não cria esses índices sozinho.
- `V6` cria hotéis, quartos, carros e componentes, com seed de UUIDs fixos: hotéis `ee1e…`, quartos `ee2e…`, carros `ca2e…`, componentes `cc0e…`.

## Consequências

- **Mudança incompatível:** os GETs de lista deixam de retornar array e passam a retornar `{ content, page, ... }`. Ainda não há frontend consumindo a API.
- `DELETE /travel-packages/{id}` agora exclui de fato. Antes retornava 204 sem excluir. Pacotes com avaliações ou favoritos retornam 409; o caminho para tirá-los do ar é inativar pelo `status`.
- Novos endpoints públicos em GET e restritos a ADMIN na escrita: `/hotels`, `/room-offers` e `/car-rental-offers`.
- Os nomes `client_flights`/`myFlights` continuam enganosos, porque apontam para pacotes. A revisão fica para a Fase 8 ("Minhas viagens"), junto com o modelo de Trip.
