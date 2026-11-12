package br.com.familhas.payment.stripe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.stripe.Stripe;

/**
 * Eventos do Stripe para testes: JSON no formato da API e assinatura Stripe-Signature calculada do mesmo jeito
 * que o Stripe (HMAC-SHA256 de "timestamp.payload" com o webhook secret).
 */
public final class StripeEvents {

    public static final String WEBHOOK_SECRET = "whsec_test_familhas";

    private StripeEvents() {
    }

    public static String sign(String payload, String secret, Instant at) {
        try {
            long timestamp = at.getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String sign(String payload) {
        return sign(payload, WEBHOOK_SECRET, Instant.now());
    }

    public static String event(String eventId, String type, String objectJson) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"livemode":false,
                 "pending_webhooks":1,"type":"%s","data":{"object":%s}}"""
                .formatted(eventId, Stripe.API_VERSION, Instant.now().getEpochSecond(), type, objectJson);
    }

    public static String session(String sessionId, UUID paymentId, String status, String paymentStatus,
            String paymentIntentId, long amountTotal) {
        return """
                {"id":"%s","object":"checkout.session","status":"%s","payment_status":"%s",
                 "payment_intent":%s,"amount_total":%d,"currency":"brl","mode":"payment",
                 "metadata":{"paymentId":"%s"}}"""
                .formatted(sessionId, status, paymentStatus, quoted(paymentIntentId), amountTotal, paymentId);
    }

    public static String paymentIntent(String intentId, UUID paymentId, String status, long amountReceived,
            String declineCode) {
        String error = declineCode == null ? "null"
                : "{\"type\":\"card_error\",\"code\":\"card_declined\",\"decline_code\":\"" + declineCode + "\"}";
        return """
                {"id":"%s","object":"payment_intent","status":"%s","amount":%d,"amount_received":%d,
                 "currency":"brl","metadata":{"paymentId":"%s"},"last_payment_error":%s}"""
                .formatted(intentId, status, amountReceived, amountReceived, paymentId, error);
    }

    public static String charge(String chargeId, String intentId, long amount, long amountRefunded) {
        return """
                {"id":"%s","object":"charge","payment_intent":"%s","amount":%d,"amount_refunded":%d,
                 "refunded":%s,"currency":"brl","metadata":{}}"""
                .formatted(chargeId, intentId, amount, amountRefunded, amount == amountRefunded);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
