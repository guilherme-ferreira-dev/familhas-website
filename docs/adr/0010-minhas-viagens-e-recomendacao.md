# ADR 0010: Minhas viagens derivadas da reserva e recomendação v1 por regras

- Status: aceito
- Data: 13/11/2026
- Fase: 8

## Contexto

A Fase 8 pede a página "Minhas viagens" (viagens futuras e passadas, favoritos, avaliações, status de pagamento e parcelas) e uma recomendação v1 baseada em regras, exposta por um `RecommendationService` com interface estável. A v2 (ML ou embeddings) fica só documentada (guia, seções 7 e 8).

O guia define a Trip como "derivada de Booking confirmada ou concluída". Na análise da fase, encontramos:
- nenhum código leva a reserva de `CONFIRMED` para `COMPLETED`: a transição existe em `BookingStatus`, mas ninguém a chama;
- `client_flights` (`ClientEntity.myFlights`) é um N:N cliente-pacote da época anterior ao monorepo, que se sobrepõe à Trip;
- qualquer cliente podia avaliar qualquer pacote, mesmo sem ter viajado;
- não havia endpoint para o cliente favoritar um pacote (só o CRUD admin mexia em `client_favorites`);
- `CityEntity` só tem nome, país e tipo, sem região ou tags.

Na resposta da fase, o responsável aprovou as recomendações para essas quatro primeiras questões (D2, D5, D6 e D7).

## Decisões

**D1. Sem tabela de viagens.** A viagem é uma leitura de `bookings` e `booking_items`: reserva `CONFIRMED` ou `COMPLETED` do cliente. O id da viagem é o id da reserva. Uma tabela própria duplicaria status, datas e valores que já estão no snapshot da reserva e exigiria sincronizá-los a cada webhook ou estorno. A única migration da fase (V13) cria o índice `bookings (client_id, status)`, que também serve às abas de clientes do Admin (Fase 10).

**D2. Futura ou passada pela data, sem job.** Período da viagem: menor `start_date` e maior `end_date` dos itens. A viagem é passada quando o status é `COMPLETED` ou quando o último dia (`end_date`) já ficou antes de hoje no fuso de negócio (`BusinessTime`, America/Sao_Paulo). Nos outros casos, é futura, e `inProgress` indica que já começou. A regra fica em `TripTiming` e a consulta de elegibilidade para avaliação (`BookingRepository.existsPastTrip`) repete a mesma condição em JPQL. Nenhum job muda status nesta fase. Se a Fase 10 ou outra quiser marcar `COMPLETED`, a classificação continua igual.

**D3. Listas carregadas e paginadas em memória.** Um cliente tem poucas reservas. `GET /api/me/trips?when=upcoming|past` carrega as viagens do cliente com os itens (`@EntityGraph`), classifica e pagina em memória, no formato `PageResponse`. Futuras vêm em ordem de início crescente, e passadas, de fim decrescente. Pagamentos, parcelas e avaliações do cliente são buscados em lote (sem N+1). Se o volume crescer, a classificação vira uma coluna derivada.

**D4. Pagamento da viagem.** O pagamento mostrado é o último que entrou dinheiro (`paid_at` preenchido). Sem nenhum pago, é a tentativa mais recente. As parcelas são as linhas de `installments`, hoje sempre uma à vista (ADR 0008). Nenhum status por parcela é inventado: o status é o do pagamento.

**D5. Avaliar só o que viajou.** Uma nova avaliação em `/api/me/ratings` só é aceita para um pacote de uma viagem passada do próprio cliente. Caso contrário, a API responde 422 `RATING_NOT_ALLOWED`. Avaliação repetida dá 409 `RATING_ALREADY_EXISTS`, e a de outro cliente dá 404 `RATING_NOT_FOUND`. A regra vale também ao trocar o pacote de uma avaliação existente. As avaliações do seed (anteriores à regra) continuam válidas, e o CRUD admin não muda.

**D6. Favoritos do cliente.** `GET /api/me/favorites` lista os favoritos. `PUT` e `DELETE /api/me/favorites/{packageId}` são idempotentes (204). Só pacotes ativos podem ser favoritados; um inexistente ou inativo dá 404 `PACKAGE_NOT_FOUND`. A tabela é a mesma `client_favorites`.

**D7. `client_flights` fica como legado.** Não é removido nesta fase nem usado pela Trip. `Me.myFlightIds` passa a ser marcado como `deprecated` no contrato. A remoção pode vir numa fase futura, combinada com o CRUD admin de clientes.

**D8. Recomendação v1 por regras.** Interface estável:

```java
public interface RecommendationService {
    List<Recommendation> recommend(UUID clientId, int limit);
}
```

`RuleBasedRecommendationService` monta o perfil do cliente e entrega candidatos a `RecommendationRules`, uma função pura e determinística, testada por parâmetros.

Candidatos: pacotes ativos com voo ativo e embarque depois de hoje. Ficam de fora:
- pacotes já reservados pelo cliente (qualquer reserva não cancelada);
- os que ele já favoritou (aparecem na seção de favoritos);
- os que ele avaliou com nota menor ou igual a 2.

Sinais do perfil:

| Sinal | Peso na categoria do pacote | Destino |
|---|---|---|
| Viagem (confirmada ou concluída) | +3 | país visitado |
| Favorito | +2 | país de interesse |
| Avaliação ≥ 4 | +2 | país de interesse |
| Avaliação ≤ 2 | −3 | — |

Pontuação do candidato:
- afinidade de categoria: soma dos pesos da categoria, se positiva (motivo `SIMILAR_CATEGORY`);
- destino afim: +2 se o país do destino está entre os de interesse (`SIMILAR_DESTINATION`). Sem região ou tags em `CityEntity`, "afim" é "mesmo país";
- sazonalidade: +1,5 se o mês de embarque está a até um mês de distância (circular) de um mês em que o cliente já viajou (`SEASONAL`);
- bem avaliado: +1 se a média das avaliações do pacote é ≥ 4 (`HIGHLY_RATED`);
- promoção: +0,5 se `price_promotion` é menor que `price` (`ON_PROMOTION`).

A ordem é: pontuação decrescente, embarque mais próximo, depois id, para ser determinística. O `limit` vai de 1 a 20 (padrão 6). Cliente sem histórico (cold start) recebe os mesmos candidatos ordenados só por avaliação, promoção e data. Cada recomendação traz `reasons[]`, para a Loja explicar o porquê ("Porque você gosta de praia").

Os candidatos são carregados em memória: o catálogo é curado pelo Enéas (dezenas de pacotes). Os pesos ficam em constantes, documentados aqui. Ajustá-los é uma mudança de código com teste.

**D9. Erros e segurança.** Tudo fica em `/api/me/**` (`ROLE_USER` ou `ROLE_ADMIN`), e o cliente vem sempre do `sub` do token. A viagem de outro cliente responde 404 (não 403), para não revelar que existe. As respostas são DTOs, e os erros, Problem Details com `code`. O contrato fica em `docs/api/minhas-viagens.yaml`.

## v2 (só documentada)

- **Filtragem colaborativa:** matriz cliente × pacote (reservas, favoritos, avaliações) com fatoração (ALS) ou vizinhos mais próximos. Faz sentido só com volume de clientes que hoje não existe.
- **Embeddings de conteúdo:** vetor por pacote (título, descrição, categoria, destino, componentes) e por cliente (média ponderada do histórico), com busca por similaridade (Oracle 23ai `VECTOR` ou serviço externo). Pede descrições ricas dos pacotes e a definição do provedor de embeddings (custo, LGPD).
- **Aprendizado com cliques:** registrar impressões e cliques das recomendações (com consentimento) para medir CTR e ajustar pesos com teste A/B.
- **Compatibilidade:** a v2 entra como outra implementação de `RecommendationService`, escolhida por propriedade, mantendo o contrato `GET /api/me/recommendations` e os `reasons`.

## Consequências

- Nenhuma tabela nova. Status e valores da viagem sempre batem com a reserva e o pagamento, sem sincronização.
- A paginação em memória serve ao volume atual. Um cliente com centenas de reservas pediria a coluna derivada citada em D3.
- "Passada" depende da data dos itens. Uma reserva confirmada e nunca marcada `COMPLETED` aparece como passada no dia seguinte ao fim.
- A recomendação v1 é explicável e testável, mas limitada pelo modelo de cidade (afinidade só por país).
