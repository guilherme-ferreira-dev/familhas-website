package br.com.familhas.payment.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.familhas.common.Money;
import br.com.familhas.payment.PaymentGateway.CheckoutRequest;
import br.com.familhas.payment.PaymentGateway.CheckoutSession;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentProperties;

/**
 * Adapter contra o stripe-mock: valida que cada chamada (parâmetros, idempotency key, expand) é aceita pela
 * especificação OpenAPI do Stripe, sem conta nem rede externa (ADR 0007, D11). O stripe-mock devolve fixtures:
 * aqui só a forma da chamada e da resposta importa, não os valores.
 */
@Testcontainers(disabledWithoutDocker = true)
class StripeMockGatewayIntegrationTests {

    public static final String STRIPE_MOCK_IMAGE = "stripe/stripe-mock:v0.206.0";

    @Container
    static GenericContainer<?> stripeMock = new GenericContainer<>(STRIPE_MOCK_IMAGE)
            .withExposedPorts(12111)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofMinutes(2));

    static StripePaymentGateway gateway;

    @BeforeAll
    static void setUp() {
        gateway = new StripePaymentGateway(
                new StripeProperties("sk_test_123", StripeEvents.WEBHOOK_SECRET, apiBase(stripeMock), null, null, 0),
                new PaymentProperties(null, null, null, null, null, null, 0));
    }

    public static String apiBase(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(12111);
    }

    @Test
    void cardCheckoutSessionIsAccepted() {
        CheckoutSession session = gateway.createCheckoutSession(request(PaymentMethod.CREDIT_CARD));

        assertThat(session.id()).isNotBlank();
        assertThat(session.url()).isNotBlank();
        assertThat(session.expiresAt()).isNotNull();
    }

    @Test
    void pixCheckoutSessionWithExpiryIsAccepted() {
        assertThat(gateway.createCheckoutSession(request(PaymentMethod.PIX)).id()).isNotBlank();
    }

    @Test
    void expireRetrieveAndRefundAreAccepted() {
        assertThat(gateway.isAvailable()).isTrue();
        assertThatCode(() -> gateway.expireCheckoutSession("cs_test_123", "expire:" + UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> gateway.fetchCheckoutSessionState("cs_test_123")).doesNotThrowAnyException();
        assertThatCode(() -> gateway.refund("pi_123", "refund:" + UUID.randomUUID())).doesNotThrowAnyException();
    }

    private static CheckoutRequest request(PaymentMethod method) {
        UUID booking = UUID.randomUUID();
        return new CheckoutRequest(UUID.randomUUID(), booking, method, Money.of("7830.16", "BRL"),
                "Reserva Familhas", "ana@exemplo.com", "http://localhost:4200/checkout/" + booking + "/sucesso",
                "http://localhost:4200/checkout/" + booking + "/cancelado",
                Instant.now().plus(Duration.ofMinutes(30)), "checkout-session:" + UUID.randomUUID());
    }
}
