package br.com.familhas.provider;

/** Parte comum de toda oferta de provedor. */
public interface ProviderOffer {

    /** Identificador estável, no formato {@code <provider>:<id do provedor>}. */
    String offerId();

    String provider();

    static String id(String provider, String providerOfferId) {
        return provider + ":" + providerOfferId;
    }

    static String providerOf(String offerId) {
        int separator = offerId == null ? -1 : offerId.indexOf(':');
        return separator <= 0 ? "" : offerId.substring(0, separator);
    }

    static String localIdOf(String offerId) {
        int separator = offerId == null ? -1 : offerId.indexOf(':');
        return separator < 0 ? "" : offerId.substring(separator + 1);
    }
}
