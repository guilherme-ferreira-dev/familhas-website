package br.com.familhas.payment;

import static br.com.familhas.payment.stripe.StripeEvents.charge;
import static br.com.familhas.payment.stripe.StripeEvents.event;
import static br.com.familhas.payment.stripe.StripeEvents.paymentIntent;
import static br.com.familhas.payment.stripe.StripeEvents.session;
import static br.com.familhas.payment.stripe.StripeEvents.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.booking.BookingRepository;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.messaging.OutboxEventEntity;
import br.com.familhas.messaging.OutboxEventRepository;
import br.com.familhas.payment.PaymentGateway.CheckoutRequest;
import br.com.familhas.payment.PaymentGateway.CheckoutSession;
import br.com.familhas.payment.stripe.StripeEvents;
import br.com.familhas.payment.stripe.StripePaymentGateway;
import br.com.familhas.payment.webhook.StripeWebhookEventRepository;

/**
 * Fluxo de pagamento ponta a ponta (guia, Fase 6; ADR 0007): HTTP com JWT -> cotação -> reserva -> Stripe
 * Checkout (stripe-mock) -> webhook assinado -> reserva CONFIRMED, com Oracle real.
 *
 * O stripe-mock devolve sempre a mesma sessão fixa (mesmo id, validade no passado). O spy chama o adapter real,
 * que fala com o stripe-mock, e só troca id e validade na resposta, para cada teste ter a própria sessão.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.stripe.secret-key=sk_test_familhas",
        "familhas.stripe.webhook-secret=" + StripeEvents.WEBHOOK_SECRET,
        "familhas.stripe.max-network-retries=0" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentFlowIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @Container
    static GenericContainer<?> stripeMock = new GenericContainer<>(StripeEvents.STRIPE_MOCK_IMAGE)
            .withExposedPorts(StripeEvents.STRIPE_MOCK_PORT)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void stripe(DynamicPropertyRegistry registry) {
        registry.add("familhas.stripe.api-base", () -> StripeEvents.apiBase(stripeMock));
    }

    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");
    static final long TOTAL_CENTS_2_PAX = 783016;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FlightRepository flightRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private StripeWebhookEventRepository webhookEventRepository;
    @Autowired private PaymentReconciliationService reconciliation;
    @Autowired private TransactionTemplate transactionTemplate;
    @MockitoSpyBean private StripePaymentGateway gateway;

    private String subject;

    @BeforeEach
    void setUp() {
        subject = "kc-" + UUID.randomUUID();
        doAnswer(invocation -> {
            CheckoutRequest request = invocation.getArgument(0);
            CheckoutSession fixture = (CheckoutSession) invocation.callRealMethod();
            return new CheckoutSession("cs_test_" + request.paymentId(), fixture.url(), request.expiresAt());
        }).when(gateway).createCheckoutSession(any());
    }

    @Test
    void cardPaymentConfirmedByWebhookConfirmsTheBookingOnceEvenIfReplayed() throws Exception {
        int seatsBefore = seats();
        UUID bookingId = book();
        JsonNode checkout = checkout(bookingId, "CREDIT_CARD").andExpect(status().isCreated()).json();
        UUID paymentId = UUID.fromString(checkout.get("paymentId").asText());
        assertThat(checkout.get("checkoutUrl").asText()).isNotBlank();
        assertThat(seats()).isEqualTo(seatsBefore - 2);

        // Repetir o POST com o mesmo método devolve a mesma sessão.
        checkout(bookingId, "CREDIT_CARD").andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()));

        String paid = event("evt_" + UUID.randomUUID(), "checkout.session.completed",
                session("cs_test_" + paymentId, paymentId, "complete", "paid", "pi_" + paymentId, TOTAL_CENTS_2_PAX));
        webhook(paid, sign(paid)).andExpect(status().isOk());
        webhook(paid, sign(paid)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.method").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.payment.installments").value(1));
        assertThat(eventsOf("Booking", bookingId)).extracting(OutboxEventEntity::getEventType)
                .containsExactly("BookingCreated");
        assertThat(eventsOf("Payment", paymentId)).extracting(OutboxEventEntity::getEventType)
                .containsExactly("PaymentConfirmed");
        assertThat(webhookEventRepository.findById(objectMapper.readTree(paid).get("id").asText())).get()
                .satisfies(e -> assertThat(e.getOutcome().name()).isEqualTo("PROCESSED"));
        assertThat(seats()).as("assentos continuam com a reserva confirmada").isEqualTo(seatsBefore - 2);
    }

    @Test
    void pixGoesThroughProcessingBlocksANewCheckoutAndThenConfirms() throws Exception {
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "PIX").andExpect(status().isCreated()).json());

        String completed = event("evt_" + UUID.randomUUID(), "checkout.session.completed",
                session("cs_test_" + paymentId, paymentId, "complete", "unpaid", "pi_" + paymentId, TOTAL_CENTS_2_PAX));
        webhook(completed, sign(completed)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.payment.status").value("PROCESSING"));
        checkout(bookingId, "CREDIT_CARD").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        String succeeded = event("evt_" + UUID.randomUUID(), "checkout.session.async_payment_succeeded",
                session("cs_test_" + paymentId, paymentId, "complete", "paid", "pi_" + paymentId, TOTAL_CENTS_2_PAX));
        webhook(succeeded, sign(succeeded)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.method").value("PIX"));
    }

    @Test
    void declinedCardPublishesPaymentFailedAndTheRetryInTheSameSessionConfirms() throws Exception {
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "CREDIT_CARD").json());

        String declined = event("evt_" + UUID.randomUUID(), "payment_intent.payment_failed",
                paymentIntent("pi_" + paymentId, paymentId, "requires_payment_method", 0, "insufficient_funds"));
        webhook(declined, sign(declined)).andExpect(status().isOk());
        getBooking(bookingId).andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

        String succeeded = event("evt_" + UUID.randomUUID(), "payment_intent.succeeded",
                paymentIntent("pi_" + paymentId, paymentId, "succeeded", TOTAL_CENTS_2_PAX, null));
        webhook(succeeded, sign(succeeded)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(eventsOf("Payment", paymentId)).extracting(OutboxEventEntity::getEventType)
                .containsExactlyInAnyOrder("PaymentFailed", "PaymentConfirmed");
    }

    @Test
    void fullRefundCancelsTheBookingAndReturnsTheSeats() throws Exception {
        int seatsBefore = seats();
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "CREDIT_CARD").json());
        String paid = event("evt_" + UUID.randomUUID(), "checkout.session.completed",
                session("cs_test_" + paymentId, paymentId, "complete", "paid", "pi_" + paymentId, TOTAL_CENTS_2_PAX));
        webhook(paid, sign(paid)).andExpect(status().isOk());

        String partial = event("evt_" + UUID.randomUUID(), "charge.refunded",
                charge("ch_" + paymentId, "pi_" + paymentId, TOTAL_CENTS_2_PAX, 100000));
        webhook(partial, sign(partial)).andExpect(status().isOk());
        getBooking(bookingId).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.payment.refundedAmount").value(1000.00));

        String full = event("evt_" + UUID.randomUUID(), "charge.refunded",
                charge("ch_" + paymentId, "pi_" + paymentId, TOTAL_CENTS_2_PAX, TOTAL_CENTS_2_PAX));
        webhook(full, sign(full)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("REFUNDED"))
                .andExpect(jsonPath("$.payment.status").value("REFUNDED"));
        assertThat(seats()).isEqualTo(seatsBefore);
    }

    @Test
    void forgedOrTamperedWebhookChangesNothing() throws Exception {
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "CREDIT_CARD").json());
        String paid = event("evt_" + UUID.randomUUID(), "checkout.session.completed",
                session("cs_test_" + paymentId, paymentId, "complete", "paid", "pi_" + paymentId, TOTAL_CENTS_2_PAX));

        webhook(paid, sign(paid, "whsec_falso", Instant.now())).andExpect(status().isBadRequest());
        webhook(paid, null).andExpect(status().isBadRequest());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        assertThat(webhookEventRepository.existsById(objectMapper.readTree(paid).get("id").asText())).isFalse();
    }

    @Test
    void paymentWithADifferentAmountConfirmsNothing() throws Exception {
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "CREDIT_CARD").json());
        String paid = event("evt_" + UUID.randomUUID(), "checkout.session.completed",
                session("cs_test_" + paymentId, paymentId, "complete", "paid", "pi_" + paymentId, 100));

        webhook(paid, sign(paid)).andExpect(status().isOk());

        getBooking(bookingId).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void reconciliationCancelsAnExpiredUnpaidBookingAndReturnsTheSeats() throws Exception {
        int seatsBefore = seats();
        UUID bookingId = book();
        UUID paymentId = paymentIdOf(checkout(bookingId, "PIX").json());
        transactionTemplate.executeWithoutResult(status -> {
            bookingRepository.findById(bookingId).orElseThrow().setExpiresAt(Instant.now().minusSeconds(60));
            paymentRepository.findById(paymentId).orElseThrow().setSessionExpiresAt(Instant.now().minusSeconds(1));
        });

        reconciliation.reconcile();

        getBooking(bookingId).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("PAYMENT_EXPIRED"))
                .andExpect(jsonPath("$.payment.status").value("CANCELED"));
        assertThat(seats()).isEqualTo(seatsBefore);
        checkout(bookingId, "PIX").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_PAYABLE"));
    }

    private UUID book() throws Exception {
        JsonNode quote = json(mockMvc.perform(asUser(post("/api/me/quotes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"travelPackageId\":\"" + PACKAGE_MACEIO + "\",\"passengers\":2}"))
                .andExpect(status().isCreated()));
        JsonNode booking = json(mockMvc.perform(asUser(post("/api/me/bookings")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"" + quote.get("id").asText() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(7830.16)));
        return UUID.fromString(booking.get("id").asText());
    }

    private Result checkout(UUID bookingId, String method) throws Exception {
        return new Result(mockMvc.perform(asUser(post("/api/me/bookings/" + bookingId + "/checkout-session"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"" + method + "\"}")));
    }

    private ResultActions getBooking(UUID bookingId) throws Exception {
        return mockMvc.perform(asUser(get("/api/me/bookings/" + bookingId)));
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
                .content(payload);
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(subject).claim("email", subject + "@exemplo.com")
                        .claim("email_verified", true).claim("given_name", "Ana").claim("name", "Ana Souza"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private java.util.List<OutboxEventEntity> eventsOf(String aggregateType, UUID id) {
        return outboxRepository.findByAggregateTypeAndAggregateId(aggregateType, id.toString());
    }

    private int seats() {
        return flightRepository.findById(FLIGHT_GOL).orElseThrow().getSeatsAvailable();
    }

    private UUID paymentIdOf(JsonNode checkout) {
        return UUID.fromString(checkout.get("paymentId").asText());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    /** ResultActions com acesso ao corpo JSON. */
    private final class Result {

        private final ResultActions actions;

        Result(ResultActions actions) {
            this.actions = actions;
        }

        Result andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        JsonNode json() throws Exception {
            return PaymentFlowIntegrationTests.this.json(actions);
        }
    }
}
