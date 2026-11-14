package br.com.familhas.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditLogEntity;
import br.com.familhas.admin.audit.AuditLogRepository;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.payment.PaymentEntity;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.provider.flight.CuratedFlightProvider;

/**
 * Admin com Oracle real (guia, Fase 10; ADR 0011): migrations V14..V16 validadas pelo Hibernate, filtros das
 * listas, abas de clientes, planilha, exportação, dashboard, margem, cotação manual e auditoria.
 *
 * Os dados nascem pelo caminho real da Loja (cotação e reserva pela API, com JWT de cliente). O pagamento
 * aprovado é gravado direto pelo domínio, como o webhook faria (o fluxo do Stripe é coberto na Fase 6).
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false",
        "familhas.admin.payment-feed.enabled=false" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AdminIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private String subject;
    private String email;
    private UUID clientId;
    private UUID bookingId;
    private UUID paymentId;

    @BeforeEach
    void customerBooksAndPaysAPackage() throws Exception {
        subject = "kc-" + UUID.randomUUID();
        email = subject + "@exemplo.com";
        JsonNode quote = json(mockMvc.perform(asUser(post("/api/me/quotes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"travelPackageId\":\"" + PACKAGE_MACEIO + "\",\"passengers\":1}"))
                .andExpect(status().isCreated()));
        bookingId = UUID.fromString(json(mockMvc.perform(asUser(post("/api/me/bookings"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteId\":\"" + quote.get("id").asText() + "\"}"))
                .andExpect(status().isCreated())).get("id").asText());
        paymentId = transactionTemplate.execute(status -> {
            var booking = bookingRepository.findById(bookingId).orElseThrow();
            PaymentEntity payment = PaymentEntity.pending(booking, PaymentMethod.PIX);
            payment.markSucceeded(Instant.now());
            booking.confirm(Instant.now());
            clientId = booking.getClient().getId();
            return paymentRepository.save(payment).getId();
        });
    }

    @Test
    void bookingsAndPaymentsAreFilterableAndShowTheClient() throws Exception {
        admin(get("/api/admin/bookings").param("clientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.content[0].client.email").value(email))
                .andExpect(jsonPath("$.content[0].latestPaymentStatus").value("SUCCEEDED"));
        admin(get("/api/admin/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marginAmount").isNumber())
                .andExpect(jsonPath("$.items[0].unitPrice").isNumber())
                .andExpect(jsonPath("$.payments[0].id").value(paymentId.toString()));

        admin(get("/api/admin/payments").param("clientId", clientId.toString())
                .param("status", "SUCCEEDED").param("status", "REFUNDED").param("method", "PIX")
                .param("installments", "1").param("dateField", "PAID").param("from", "2020-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].client.email").value(email));
        admin(get("/api/admin/payments").param("clientId", clientId.toString()).param("method", "CREDIT_CARD"))
                .andExpect(jsonPath("$.totalElements").value(0));
        admin(get("/api/admin/payments").param("sort", "idempotencyKey,asc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportStreamsTheFilteredRowsAndIsAudited() throws Exception {
        MvcResult started = admin(get("/api/admin/payments/export").param("format", "csv")
                .param("clientId", clientId.toString()))
                .andExpect(request().asyncStarted()).andReturn();
        String csv = mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv.split("\r\n")).hasSize(2);
        assertThat(csv).contains(paymentId.toString()).contains(email);
        assertThat(audits("payments")).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.EXPORT);
            assertThat(log.getDetails()).isEqualTo("format=csv clientId=" + clientId);
            assertThat(log.getActorSubject()).isEqualTo("kc-admin");
        });
    }

    @Test
    void clientWithAConfirmedBookingIsInTheActiveTab() throws Exception {
        admin(get("/api/admin/clients").param("segment", "ACTIVE").param("q", email))
                .andExpect(jsonPath("$.totalElements").value(1));
        admin(get("/api/admin/clients").param("segment", "NEW").param("q", email))
                .andExpect(jsonPath("$.totalElements").value(0));
        admin(get("/api/admin/clients").param("segment", "PAST").param("q", email))
                .andExpect(jsonPath("$.totalElements").value(0));
        JsonNode counts = json(admin(get("/api/admin/clients/segments")).andExpect(status().isOk()));
        assertThat(counts.get("active").asLong()).isPositive();
        assertThat(counts.get("all").asLong()).isGreaterThanOrEqualTo(counts.get("active").asLong()
                + counts.get("new").asLong() + counts.get("past").asLong());
    }

    @Test
    void dashboardCountsTheApprovedPayment() throws Exception {
        admin(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue[0].currency").value("BRL"))
                .andExpect(jsonPath("$.revenue[0].count").isNumber())
                .andExpect(jsonPath("$.paymentsByStatus.SUCCEEDED").isNumber())
                .andExpect(jsonPath("$.bookingsByStatus.CONFIRMED").isNumber())
                .andExpect(jsonPath("$.dailyRevenue").isArray());
    }

    @Test
    void manualQuoteIsVisibleToTheClientAndAudited() throws Exception {
        JsonNode created = json(admin(post("/api/admin/quotes").contentType(MediaType.APPLICATION_JSON).content("""
                {"clientId":"%s","passengers":1,"flight":{"offerId":"%s"},"manualDiscount":10.00,
                 "validityHours":48,"internalNote":"Atendimento WhatsApp"}
                """.formatted(clientId, CuratedFlightProvider.offerIdOf(FLIGHT_GOL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("ADMIN"))
                .andExpect(jsonPath("$.manualDiscountAmount").value(10.00))
                .andExpect(jsonPath("$.createdBy").value("admin.familhas")));
        String quoteId = created.get("id").asText();

        JsonNode mine = json(mockMvc.perform(asUser(get("/api/me/quotes/" + quoteId))).andExpect(status().isOk()));
        assertThat(mine.get("discount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(mine.has("internalNote")).isFalse();
        assertThat(mine.toString()).doesNotContain("Atendimento WhatsApp");

        admin(get("/api/admin/quotes").param("origin", "ADMIN").param("clientId", clientId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        assertThat(audits("quotes")).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(log.getResourceId()).isEqualTo(quoteId);
            assertThat(log.getStatus()).isEqualTo(201);
        });

        admin(post("/api/admin/quotes").contentType(MediaType.APPLICATION_JSON).content("""
                {"clientId":"%s","passengers":1,"flight":{"offerId":"%s"},"manualDiscount":99999.00}
                """.formatted(clientId, CuratedFlightProvider.offerIdOf(FLIGHT_GOL))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUOTE_BELOW_COST"));
        assertThat(audits("quotes")).anySatisfy(log -> assertThat(log.getStatus()).isEqualTo(422));
    }

    @Test
    void pricingSettingsComeFromTheEnvironmentUntilSavedAndChangeTheNextQuote() throws Exception {
        JsonNode before = json(admin(get("/api/admin/pricing-settings")).andExpect(status().isOk()));
        String flight = "{\"flight\":{\"offerId\":\"" + CuratedFlightProvider.offerIdOf(FLIGHT_GOL)
                + "\"},\"passengers\":1}";
        JsonNode quoteBefore = json(mockMvc.perform(asUser(post("/api/me/quotes"))
                .contentType(MediaType.APPLICATION_JSON).content(flight)).andExpect(status().isCreated()));

        admin(put("/api/admin/pricing-settings").contentType(MediaType.APPLICATION_JSON)
                .content("{\"marginPercent\":20,\"serviceFeePerPassenger\":0,\"serviceFeeCurrency\":\"BRL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DATABASE"))
                .andExpect(jsonPath("$.updatedBy").value("admin.familhas"));
        try {
            JsonNode quoteAfter = json(mockMvc.perform(asUser(post("/api/me/quotes"))
                    .contentType(MediaType.APPLICATION_JSON).content(flight)).andExpect(status().isCreated()));
            assertThat(quoteAfter.get("total").decimalValue()).isGreaterThan(quoteBefore.get("total").decimalValue());
            assertThat(audits("pricing-settings")).anySatisfy(
                    log -> assertThat(log.getAction()).isEqualTo(AuditAction.UPDATE));
        } finally {
            admin(put("/api/admin/pricing-settings").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"marginPercent\":%s,\"serviceFeePerPassenger\":%s,\"serviceFeeCurrency\":\"%s\"}"
                            .formatted(before.get("marginPercent"), before.get("serviceFeePerPassenger"),
                                    before.get("serviceFeeCurrency").asText())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void promotionFilterAndCatalogWritesAreAudited() throws Exception {
        JsonNode page = json(admin(get("/api/admin/travel-packages").param("onPromotion", "true").param("size", "100"))
                .andExpect(status().isOk()));
        assertThat(page.get("content")).isNotEmpty().allSatisfy(p -> assertThat(p.get("pricePromotion").decimalValue())
                .isLessThan(p.get("price").decimalValue()));

        admin(put("/api/admin/flights/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(audits("flights")).anySatisfy(log -> assertThat(log.getStatus()).isEqualTo(400));
    }

    @Test
    void regularClientIsForbiddenOnTheRealStackToo() throws Exception {
        mockMvc.perform(asUser(get("/api/admin/payments"))).andExpect(status().isForbidden());
        mockMvc.perform(asUser(get("/api/admin/payments/export").param("format", "csv")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asUser(post("/api/admin/quotes")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private List<AuditLogEntity> audits(String resource) {
        return auditLogRepository.findAll().stream().filter(log -> log.getResource().equals(resource)).toList();
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(jwt()
                .jwt(j -> j.subject("kc-admin").claim("preferred_username", "admin.familhas"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(subject).claim("email", email)
                        .claim("email_verified", true).claim("given_name", "Ana").claim("name", "Ana Souza"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
