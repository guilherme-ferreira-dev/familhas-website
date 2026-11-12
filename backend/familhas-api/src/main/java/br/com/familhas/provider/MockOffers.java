package br.com.familhas.provider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Apoio aos adapters mock: preço determinístico por "semente" e offerId que carrega os dados da oferta,
 * para findOffer reconstruir exatamente a mesma oferta sem guardar estado.
 */
public final class MockOffers {

    public static final String NAME = "mock";

    private MockOffers() {
    }

    /** Número estável em [0, bound) derivado do texto (mesma entrada, mesmo preço). */
    public static int stable(String seed, int bound) {
        return Math.floorMod(seed.hashCode(), bound);
    }

    public static String encode(String... parts) {
        String joined = String.join("|", parts);
        return ProviderOffer.id(NAME, Base64.getUrlEncoder().withoutPadding()
                .encodeToString(joined.getBytes(StandardCharsets.UTF_8)));
    }

    public static Optional<String[]> decode(String offerId, int expectedParts) {
        if (!NAME.equals(ProviderOffer.providerOf(offerId))) {
            return Optional.empty();
        }
        try {
            String joined = new String(Base64.getUrlDecoder().decode(ProviderOffer.localIdOf(offerId)),
                    StandardCharsets.UTF_8);
            String[] parts = joined.split("\\|", -1);
            return parts.length == expectedParts ? Optional.of(parts) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
