package br.com.familhas.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.CancellationReason;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.payment.PaymentGateway.CheckoutRequest;
import br.com.familhas.payment.PaymentGateway.CheckoutSession;

class CheckoutServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID BOOKING = UUID.fromString("b00c0000-0000-4000-8000-000000000001");

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final List<PaymentEntity> attempts = new ArrayList<>();
    private BookingEntity booking;

    @BeforeEach
    void setUp() {
        booking = booking(NOW.plus(Duration.ofMinutes(20)));
        when(bookings.lockById(BOOKING)).thenReturn(Optional.of(booking));
        when(payments.findByBookingIdOrderByCreatedAtDesc(BOOKING)).thenReturn(attempts);
        when(payments.saveAndFlush(any(PaymentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.createCheckoutSession(any())).thenAnswer(i -> new CheckoutSession("cs_test_new",
                "https://checkout.stripe.com/c/pay/cs_test_new", ((CheckoutRequest) i.getArgument(0)).expiresAt()));
    }

    @Test
    void firstCheckoutCreatesASessionWithServerSideReturnUrlsAndTheBookingTotal() {
        CheckoutService.CheckoutResult result = service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD);

        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(result.created()).isTrue();
        assertThat(result.response().checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_new");
        assertThat(request.getValue().amount().amount()).isEqualByComparingTo("7830.16");
        assertThat(request.getValue().successUrl()).isEqualTo("https://loja.familhas.com.br/checkout/" + BOOKING + "/sucesso");
        assertThat(request.getValue().cancelUrl()).endsWith("/checkout/" + BOOKING + "/cancelado");
        assertThat(request.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(request.getValue().idempotencyKey()).isEqualTo("checkout-session:" + result.response().paymentId());
        assertThat(request.getValue().description()).doesNotContain("Ana");
    }

    @Test
    void openSessionWithTheSameMethodIsReusedWithoutCallingStripe() {
        PaymentEntity open = attempt(PaymentMethod.CREDIT_CARD, NOW.plusSeconds(600));
        attempts.add(open);

        CheckoutService.CheckoutResult result = service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD);

        assertThat(result.created()).isFalse();
        assertThat(result.response().paymentId()).isEqualTo(open.getId());
        verify(gateway, never()).createCheckoutSession(any());
    }

    @Test
    void switchingMethodExpiresThePreviousSession() {
        PaymentEntity card = attempt(PaymentMethod.CREDIT_CARD, NOW.plusSeconds(600));
        attempts.add(card);

        CheckoutService.CheckoutResult result = service(true).open(CLIENT, BOOKING, PaymentMethod.PIX);

        verify(gateway).expireCheckoutSession("cs_test_old", "expire:" + card.getId());
        assertThat(card.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.created()).isTrue();
        assertThat(result.response().paymentMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void processingPixBlocksANewCheckout() {
        PaymentEntity pix = attempt(PaymentMethod.PIX, NOW.minusSeconds(1));
        pix.markProcessing();
        attempts.add(pix);

        assertThatThrownBy(() -> service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void expiredPaymentWindowIsConflict() {
        booking.setExpiresAt(NOW);

        assertThatThrownBy(() -> service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code()).isEqualTo("BOOKING_EXPIRED"));
    }

    @Test
    void openSessionIsStillReusedAfterTheBookingDeadline() {
        booking.setExpiresAt(NOW.minusSeconds(60));
        attempts.add(attempt(PaymentMethod.CREDIT_CARD, NOW.plusSeconds(600)));

        assertThat(service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD).created()).isFalse();
    }

    @Test
    void cancelledBookingIsNotPayable() {
        booking.cancel(CancellationReason.PAYMENT_EXPIRED, NOW);

        assertThatThrownBy(() -> service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("BOOKING_NOT_PAYABLE"));
    }

    @Test
    void pixDisabledIsUnprocessable() {
        assertThatThrownBy(() -> service(false).open(CLIENT, BOOKING, PaymentMethod.PIX))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("PAYMENT_METHOD_UNAVAILABLE"));
    }

    @Test
    void withoutStripeConfiguredTheCheckoutIsUnavailable() {
        when(gateway.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service(true).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void bookingOfAnotherClientIs404() {
        assertThatThrownBy(() -> service(true).open(UUID.randomUUID(), BOOKING, PaymentMethod.CREDIT_CARD))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(gateway, never()).createCheckoutSession(eq(null));
    }

    private CheckoutService service(boolean pixEnabled) {
        return new CheckoutService(bookings, payments, gateway,
                new PaymentProperties(URI.create("https://loja.familhas.com.br"), null, pixEnabled, null, null, null,
                        0),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PaymentEntity attempt(PaymentMethod method, Instant sessionExpiresAt) {
        PaymentEntity payment = PaymentEntity.pending(booking, method);
        payment.setStripeCheckoutSessionId("cs_test_old");
        payment.setCheckoutUrl("https://checkout.stripe.com/c/pay/cs_test_old");
        payment.setSessionExpiresAt(sessionExpiresAt);
        return payment;
    }

    private static BookingEntity booking(Instant expiresAt) {
        ClientEntity client = new ClientEntity();
        client.setId(CLIENT);
        client.setEmail("ana@exemplo.com");
        client.setName("Ana Souza");
        BookingEntity booking = new BookingEntity();
        booking.setId(BOOKING);
        booking.setClient(client);
        booking.setTotalAmount(new BigDecimal("7830.16"));
        booking.setCurrency("BRL");
        booking.setExpiresAt(expiresAt);
        return booking;
    }
}
