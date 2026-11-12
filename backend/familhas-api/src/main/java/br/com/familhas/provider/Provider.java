package br.com.familhas.provider;

import java.util.List;
import java.util.Optional;

/**
 * Fonte de ofertas de um tipo (voo, hotel ou carro). Os adapters atuais são o catálogo curado (banco) e o mock;
 * provedores externos (HotelDO, Duffel/Amadeus, Rentcars) entram como novos adapters (ADR 0006).
 *
 * Contrato:
 * - search não lança exceção para "nada encontrado": retorna lista vazia;
 * - falhas técnicas (timeout, HTTP, parsing) podem lançar; o ProviderGateway isola cada provedor;
 * - offerId começa com {@code name() + ":"}, para o gateway achar o provedor de uma oferta;
 * - findOffer reconsulta a oferta com preço atual, sem cache (usado na cotação).
 */
public interface Provider<C, O extends ProviderOffer> {

    String name();

    ProviderType type();

    List<O> search(C criteria);

    Optional<O> findOffer(String offerId);
}
