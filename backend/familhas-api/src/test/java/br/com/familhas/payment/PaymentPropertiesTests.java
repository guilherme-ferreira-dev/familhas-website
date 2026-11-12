package br.com.familhas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.familhas.payment.stripe.StripeProperties;

class PaymentPropertiesTests {

    @ParameterizedTest
    @ValueSource(strings = { "sk_live_abc", "rk_live_abc", "pk_test_abc", "qualquer" })
    void onlyTestSecretKeysAreAccepted(String key) {
        assertThatThrownBy(() -> new StripeProperties(key, null, null, null, null, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chave de teste")
                .hasMessageNotContaining(key);
    }

    @Test
    void testKeysAreAcceptedAndNeverPrinted() {
        StripeProperties properties = new StripeProperties("sk_test_segredo", "whsec_segredo", null, null, null, 2);

        assertThat(properties.apiConfigured()).isTrue();
        assertThat(properties.webhookConfigured()).isTrue();
        assertThat(properties.toString()).doesNotContain("segredo");
        assertThat(new StripeProperties("rk_test_restrita", null, null, null, null, 2).apiConfigured()).isTrue();
    }

    @Test
    void withoutKeysTheApiStillStarts() {
        StripeProperties properties = new StripeProperties(" ", "", null, null, null, -1);

        assertThat(properties.apiConfigured()).isFalse();
        assertThat(properties.webhookConfigured()).isFalse();
        assertThat(properties.webhookTolerance()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxNetworkRetries()).isZero();
    }

    @Test
    void webhookSecretMustBeAStripeSigningSecret() {
        assertThatThrownBy(() -> new StripeProperties(null, "abc", null, null, null, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void checkoutSessionTtlFollowsStripeLimits() {
        assertThatThrownBy(() -> payments(Duration.ofMinutes(29))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payments(Duration.ofHours(25))).isInstanceOf(IllegalStateException.class);
        assertThat(payments(null).checkoutSessionTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void returnUrlsAreBuiltFromTheConfiguredBaseOnly() {
        UUID booking = UUID.fromString("b00c0000-0000-4000-8000-000000000001");
        PaymentProperties properties = new PaymentProperties(URI.create("https://loja.familhas.com.br/"), null, null,
                null, null, null, 0);

        assertThat(properties.returnUrl(booking, "sucesso"))
                .isEqualTo("https://loja.familhas.com.br/checkout/" + booking + "/sucesso");
        assertThat(properties.pixEnabled()).isTrue();
        assertThatThrownBy(() -> new PaymentProperties(URI.create("javascript:alert(1)"), null, null, null, null,
                null, 0)).isInstanceOf(IllegalStateException.class);
    }

    private static PaymentProperties payments(Duration ttl) {
        return new PaymentProperties(null, ttl, null, null, null, null, 0);
    }
}
