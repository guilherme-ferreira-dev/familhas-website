package br.com.familhas.recommendation;

/** Por que o pacote foi recomendado (a Loja traduz para o cliente). ADR 0010, D8. */
public enum RecommendationReason {
    /** Categoria de viagens, favoritos ou avaliações boas do cliente. */
    SIMILAR_CATEGORY,
    /** Destino no mesmo país de viagens, favoritos ou avaliações boas. */
    SIMILAR_DESTINATION,
    /** Embarque perto da época do ano em que o cliente costuma viajar. */
    SEASONAL,
    /** Média das avaliações do pacote maior ou igual a 4. */
    HIGHLY_RATED,
    /** Preço promocional menor que o preço cheio. */
    ON_PROMOTION
}
