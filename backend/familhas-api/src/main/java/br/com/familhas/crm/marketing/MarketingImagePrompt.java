package br.com.familhas.crm.marketing;

/**
 * Prompt da imagem de fundo dos posts (ADR 0019). Em inglês, que o modelo segue melhor. Sempre sem texto, logo ou
 * marca d'água: título, preço, logo e rodapé são compostos no navegador (ADR 0016). O navio é ilustrativo: o
 * modelo não conhece cada navio, então o pedido é "inspirado em", sem o nome pintado no casco.
 */
final class MarketingImagePrompt {

    static final String NO_TEXT = " Vertical composition with calm areas at the top and bottom for text overlay."
            + " Absolutely no text, letters, numbers, logos, signs or watermarks anywhere in the image.";

    private MarketingImagePrompt() {
    }

    static String of(ImageRequest request) {
        String name = clean(request.name());
        return switch (request.subject()) {
            case CITY -> "Professional travel photograph of " + name + ", showing its most recognizable scenery or"
                    + " landmark. Natural daylight, vibrant but realistic colors, inviting holiday mood, no people"
                    + " in the foreground." + NO_TEXT;
            case SHIP -> "Professional photograph of a modern ocean cruise ship inspired by " + name + ", sailing"
                    + " on calm turquoise water under a clear blue sky, seen from a flattering three-quarter angle."
                    + " Do not paint any name on the hull." + NO_TEXT;
        };
    }

    /** Só o nome, numa linha: sem controle, aspas ou quebras que mudem a instrução. */
    static String clean(String name) {
        return name.replaceAll("[\\p{Cntrl}\"`{}<>]", " ").replaceAll("\\s+", " ").strip();
    }
}
