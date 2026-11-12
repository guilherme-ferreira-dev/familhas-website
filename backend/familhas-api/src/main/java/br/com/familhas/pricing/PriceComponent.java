package br.com.familhas.pricing;

import java.util.Objects;

import br.com.familhas.common.Money;

/**
 * Componente de custo, sem margem.
 *
 * @param quantity passageiros (voo), noites x quartos (hotel) ou dias (carro)
 */
public record PriceComponent(PriceItemType type, String description, Money unitPrice, int quantity) {

    public PriceComponent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
    }
}
