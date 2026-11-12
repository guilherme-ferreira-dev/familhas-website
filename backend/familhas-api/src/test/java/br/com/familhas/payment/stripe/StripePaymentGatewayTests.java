package br.com.familhas.payment.stripe;

import static br.com.familhas.payment.stripe.StripeEvents.WEBHOOK_SECRET;
import static br.com.familhas.payment.stripe.StripeEvents.charge;
import static br.com.familhas.payment.stripe.StripeEvents.event;
import static br.com.familhas.payment.stripe.StripeEvents.paymentIntent;
import static br.com.familhas.payment.stripe.StripeEvents.session;
import static br.com.familhas.payment.stripe.StripeEvents.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.familhas.payment.InvalidWebhookException;
import br.com.familhas.payment.PaymentGatewayException;
import br.com.familhas.payment.PaymentNotification;
import br.com.familhas.payment.PaymentNotification.Kind;
import br.com.familhas.payment.PaymentProperties;

/** Assinatura e tradução dos webhooks, sem rede: o JSON segue o formato da API do Stripe. */
class StripePaymentGatewayTests {

    private static final UUID PAYMENT = UUID.fromString("9a7e0000-0000-4000-8000-000000000001");

    private final StripePaymentGateway gateway = new StripePaymentGateway(
            new StripeProperties(null, WEBHOOK_SECRET, null, Duration.ofMinutes(5), null, 0),
            new PaymentProperties(null, null, null, null, null, null, 0));

    @Test
    void paidCardSessionIsSuccessWithTheAmountAndThePaymentIdFromMetadata() {
        String payload = event("evt_1", "checkout.session.completed",
                session("cs_test_1", PAYMENT, "complete", "paid", "pi_1", 783016));

        PaymentNotification notification = gateway.parseWebhook(payload, sign(payload));

        assertThat(notification.eventId()).isEqualTo("evt_1");
        assertThat(notification.kind()).isEqualTo(Kind.SUCCEEDED);
        assertThat(notification.paymentId()).isEqualTo(PAYMENT);
        assertThat(notification.checkoutSessionId()).isEqualTo("cs_test_1");
        assertThat(notification.paymentIntentId()).isEqualTo("pi_1");
        assertThat(notification.amount()).isEqualByComparingTo("7830.16");
        assertThat(notification.currency()).isEqualTo("BRL");
    }

    @Test
    void completedPixSessionWithoutPaymentYetIsProcessing() {
        String payload = event("evt_2", "checkout.session.completed",
                session("cs_test_2", PAYMENT, "complete", "unpaid", "pi_2", 10000));

        assertThat(gateway.parseWebhook(payload, sign(payload)).kind()).isEqualTo(Kind.PROCESSING);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "checkout.session.async_payment_succeeded, SUCCEEDED",
            "checkout.session.async_payment_failed, FAILED",
            "checkout.session.expired, SESSION_EXPIRED" })
    void otherSessionEvents(String type, Kind kind) {
        String payload = event("evt_" + type, type, session("cs_test_3", PAYMENT, "complete", "unpaid", null, 100));

        assertThat(gateway.parseWebhook(payload, sign(payload)).kind()).isEqualTo(kind);
    }

    @Test
    void declinedCardKeepsOnlyTheDeclineCode() {
        String payload = event("evt_4", "payment_intent.payment_failed",
                paymentIntent("pi_4", PAYMENT, "requires_payment_method", 0, "insufficient_funds"));

        PaymentNotification notification = gateway.parseWebhook(payload, sign(payload));

        assertThat(notification.kind()).isEqualTo(Kind.FAILED);
        assertThat(notification.failureCode()).isEqualTo("insufficient_funds");
        assertThat(notification.paymentId()).isEqualTo(PAYMENT);
    }

    @Test
    void refundCarriesTheTotalRefundedSoFar() {
        String payload = event("evt_5", "charge.refunded", charge("ch_5", "pi_5", 10000, 3000));

        PaymentNotification notification = gateway.parseWebhook(payload, sign(payload));

        assertThat(notification.kind()).isEqualTo(Kind.REFUNDED);
        assertThat(notification.paymentIntentId()).isEqualTo("pi_5");
        assertThat(notification.amountRefunded()).isEqualByComparingTo("30.00");
        assertThat(notification.paymentId()).isNull();
    }

    @Test
    void unhandledEventTypeIsIgnored() {
        String payload = event("evt_6", "customer.created", "{\"id\":\"cus_1\",\"object\":\"customer\"}");

        assertThat(gateway.parseWebhook(payload, sign(payload)).kind()).isEqualTo(Kind.IGNORED);
    }

    @Test
    void wrongSecretOrTamperedBodyIsRejected() {
        String payload = event("evt_7", "checkout.session.completed",
                session("cs_test_7", PAYMENT, "complete", "paid", "pi_7", 100));

        assertThatThrownBy(() -> gateway.parseWebhook(payload, sign(payload, "whsec_outro", Instant.now())))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> gateway.parseWebhook(payload.replace("100", "1"), sign(payload)))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> gateway.parseWebhook(payload, null)).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void replayOfAnOldSignedEventIsRejected() {
        String payload = event("evt_8", "checkout.session.completed",
                session("cs_test_8", PAYMENT, "complete", "paid", "pi_8", 100));

        assertThatThrownBy(() -> gateway.parseWebhook(payload,
                sign(payload, WEBHOOK_SECRET, Instant.now().minus(Duration.ofMinutes(6)))))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void withoutWebhookSecretEveryWebhookIsRejected() {
        StripePaymentGateway unconfigured = new StripePaymentGateway(
                new StripeProperties(null, null, null, null, null, 0),
                new PaymentProperties(null, null, null, null, null, null, 0));
        String payload = event("evt_9", "customer.created", "{\"id\":\"cus_1\",\"object\":\"customer\"}");

        assertThatThrownBy(() -> unconfigured.parseWebhook(payload, sign(payload)))
                .isInstanceOf(InvalidWebhookException.class);
        assertThat(unconfigured.isAvailable()).isFalse();
        assertThatThrownBy(() -> unconfigured.refund("pi_1", "refund:1")).isInstanceOf(PaymentGatewayException.class);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({ "7830.16, 783016", "0.01, 1", "100, 10000", "99.9, 9990" })
    void amountsAreConvertedToCentsExactly(String amount, long cents) {
        assertThat(StripePaymentGateway.toMinorUnits(new BigDecimal(amount))).isEqualTo(cents);
        assertThat(StripePaymentGateway.fromMinorUnits(cents)).isEqualByComparingTo(amount);
    }

    @Test
    void amountWithMoreThanTwoDecimalsIsNeverRounded() {
        assertThatThrownBy(() -> StripePaymentGateway.toMinorUnits(new BigDecimal("10.005")))
                .isInstanceOf(ArithmeticException.class);
    }
}
