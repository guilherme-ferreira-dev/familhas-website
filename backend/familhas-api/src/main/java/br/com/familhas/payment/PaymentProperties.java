package br.com.familhas.payment;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Checkout e reconciliação (prefixo familhas.payments; ADR 0007).
 *
 * @param webBaseUrl               base da Loja para as URLs de retorno do Stripe (FAMILHAS_WEB_BASE_URL)
 * @param checkoutSessionTtl       validade da Checkout Session e do Pix; o Stripe exige no mínimo 30 min
 * @param pixEnabled               liga o Pix (depende da conta Stripe)
 * @param reconciliationEnabled    liga o job de reconciliação; os testes desligam e chamam manualmente
 * @param reconciliationFixedDelay intervalo entre execuções do job
 * @param reconciliationStaleAfter tempo sem novidade para um pagamento PENDING/PROCESSING ser consultado no Stripe
 * @param reconciliationBatchSize  itens por execução
 */
@ConfigurationProperties(prefix = "familhas.payments")
public record PaymentProperties(
        URI webBaseUrl,
        Duration checkoutSessionTtl,
        Boolean pixEnabled,
        Boolean reconciliationEnabled,
        Duration reconciliationFixedDelay,
        Duration reconciliationStaleAfter,
        int reconciliationBatchSize) {

    public static final Duration MIN_CHECKOUT_SESSION_TTL = Duration.ofMinutes(30);

    public PaymentProperties {
        webBaseUrl = webBaseUrl == null ? URI.create("http://localhost:4200") : webBaseUrl;
        checkoutSessionTtl = checkoutSessionTtl == null ? MIN_CHECKOUT_SESSION_TTL : checkoutSessionTtl;
        pixEnabled = pixEnabled == null || pixEnabled;
        reconciliationEnabled = reconciliationEnabled == null || reconciliationEnabled;
        reconciliationFixedDelay = reconciliationFixedDelay == null ? Duration.ofMinutes(5) : reconciliationFixedDelay;
        reconciliationStaleAfter = reconciliationStaleAfter == null ? Duration.ofMinutes(10)
                : reconciliationStaleAfter;
        reconciliationBatchSize = reconciliationBatchSize <= 0 ? 50 : reconciliationBatchSize;
        if (checkoutSessionTtl.compareTo(MIN_CHECKOUT_SESSION_TTL) < 0 || checkoutSessionTtl.toHours() > 24) {
            throw new IllegalStateException("CHECKOUT_SESSION_TTL precisa ficar entre 30 min e 24 h (regra do Stripe)");
        }
        if (!"http".equals(webBaseUrl.getScheme()) && !"https".equals(webBaseUrl.getScheme())) {
            throw new IllegalStateException("FAMILHAS_WEB_BASE_URL precisa ser http(s)");
        }
    }

    /** URL de retorno do Stripe Checkout para a reserva: /checkout/{bookingId}/sucesso ou /cancelado. */
    public String returnUrl(java.util.UUID bookingId, String outcome) {
        String base = webBaseUrl.toString().replaceAll("/+$", "");
        return base + "/checkout/" + bookingId + "/" + outcome;
    }
}
