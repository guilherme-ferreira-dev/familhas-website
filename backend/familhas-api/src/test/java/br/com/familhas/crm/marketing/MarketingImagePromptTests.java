package br.com.familhas.crm.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarketingImagePromptTests {

    @Test
    void cityAndShipNeverAskForTextOrLogo() {
        String city = MarketingImagePrompt.of(new ImageRequest(ImageRequest.Subject.CITY, "Maceió"));
        assertThat(city).contains("Maceió").contains("no text").contains("logos");

        String ship = MarketingImagePrompt.of(new ImageRequest(ImageRequest.Subject.SHIP, "MSC Seaview"));
        assertThat(ship).contains("inspired by MSC Seaview").contains("Do not paint any name on the hull")
                .contains("no text");
    }

    @Test
    void nameStaysOnOneLineWithoutQuotesOrControlCharacters() {
        assertThat(MarketingImagePrompt.clean("  Porto\n\"Seguro\"\t{x}  ")).isEqualTo("Porto Seguro x");
        assertThat(MarketingImagePrompt.clean("\"\"")).isEmpty();
    }
}
