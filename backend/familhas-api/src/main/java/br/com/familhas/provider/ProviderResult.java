package br.com.familhas.provider;

import java.util.List;

/**
 * Resultado agregado dos provedores de um tipo. unavailableProviders lista quem falhou, deu timeout ou está
 * com o circuito aberto; o resto do resultado continua valendo (degradação parcial).
 */
public record ProviderResult<O>(List<O> offers, List<String> unavailableProviders) {

    public boolean partial() {
        return !unavailableProviders.isEmpty();
    }
}
