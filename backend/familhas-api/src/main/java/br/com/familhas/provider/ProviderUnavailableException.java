package br.com.familhas.provider;

/** O provedor de uma oferta não respondeu (erro, timeout ou circuito aberto). Vira 503 na API. */
public class ProviderUnavailableException extends RuntimeException {

    private final ProviderType type;
    private final String provider;

    public ProviderUnavailableException(ProviderType type, String provider) {
        super("Provedor " + provider + " (" + type + ") indisponivel no momento");
        this.type = type;
        this.provider = provider;
    }

    public ProviderType type() {
        return type;
    }

    public String provider() {
        return provider;
    }
}
