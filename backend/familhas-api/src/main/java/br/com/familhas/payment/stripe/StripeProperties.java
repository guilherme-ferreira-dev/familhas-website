package br.com.familhas.payment.stripe;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe (prefixo familhas.stripe; ADR 0007, D8). Chaves só por variável de ambiente.
 *
 * A API recusa subir com chave que não seja de teste: liberar chave live exige um novo ADR.
 * Sem secretKey, a API sobe e o checkout responde 503.
 *
 * @param secretKey         STRIPE_SECRET_KEY (sk_test_... ou rk_test_...)
 * @param webhookSecret     STRIPE_WEBHOOK_SECRET (whsec_..., impresso pelo stripe listen)
 * @param apiBase           URL da API; vazio = api.stripe.com. Os testes apontam para o stripe-mock
 * @param webhookTolerance  idade máxima do evento assinado, contra replay (padrão 5 min)
 * @param timeout           timeout de conexão e de leitura nas chamadas ao Stripe
 * @param maxNetworkRetries retries do SDK em falha de rede; seguros porque toda escrita leva idempotency key
 */
@ConfigurationProperties(prefix = "familhas.stripe")
public record StripeProperties(
        String secretKey,
        String webhookSecret,
        String apiBase,
        Duration webhookTolerance,
        Duration timeout,
        int maxNetworkRetries) {

    public StripeProperties {
        secretKey = blankToNull(secretKey);
        webhookSecret = blankToNull(webhookSecret);
        apiBase = blankToNull(apiBase);
        webhookTolerance = webhookTolerance == null ? Duration.ofMinutes(5) : webhookTolerance;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        maxNetworkRetries = maxNetworkRetries < 0 ? 0 : maxNetworkRetries;
        if (secretKey != null && !secretKey.startsWith("sk_test_") && !secretKey.startsWith("rk_test_")) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY precisa ser uma chave de teste (sk_test_ ou rk_test_); chave live exige ADR");
        }
        if (webhookSecret != null && !webhookSecret.startsWith("whsec_")) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET precisa comecar com whsec_");
        }
    }

    public boolean apiConfigured() {
        return secretKey != null;
    }

    public boolean webhookConfigured() {
        return webhookSecret != null;
    }

    /** Nunca expõe as chaves (o toString do record poderia acabar em log). */
    @Override
    public String toString() {
        return "StripeProperties[apiConfigured=" + apiConfigured() + ", webhookConfigured=" + webhookConfigured()
                + ", apiBase=" + apiBase + "]";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
