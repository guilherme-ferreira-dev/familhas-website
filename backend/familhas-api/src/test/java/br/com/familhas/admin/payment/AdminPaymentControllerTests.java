package br.com.familhas.admin.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static br.com.familhas.admin.payment.PaymentFixtures.payment;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.admin.payment.feed.PaymentFeedBroadcaster;
import br.com.familhas.admin.payment.feed.PaymentFeedEvent;
import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;

@WebMvcTest(AdminPaymentController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        AdminPaymentControllerTests.FixedClock.class, PaymentFeedBroadcaster.class})
class AdminPaymentControllerTests {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2027-01-15T13:05:00Z"), ZoneOffset.UTC);
        }
    }

    static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminPaymentService paymentService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private AuditService auditService;
    @Autowired private PaymentFeedBroadcaster feed;

    @Test
    void bindsEveryFilterIncludingRepeatedStatus() throws Exception {
        when(paymentService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 50, 0, 0));
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/payments").with(ADMIN)
                        .param("from", "2027-01-01").param("to", "2027-01-31").param("dateField", "PAID")
                        .param("status", "SUCCEEDED").param("status", "REFUNDED")
                        .param("method", "PIX").param("installments", "1").param("clientId", clientId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        ArgumentCaptor<AdminPaymentFilter> filter = ArgumentCaptor.forClass(AdminPaymentFilter.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).findAll(filter.capture(), pageable.capture());
        assertThat(filter.getValue()).isEqualTo(new AdminPaymentFilter(LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31), PaymentDateField.PAID, List.of(PaymentStatus.SUCCEEDED,
                        PaymentStatus.REFUNDED), PaymentMethod.PIX, 1, clientId, null));
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void rejectsInvalidFilters() throws Exception {
        mockMvc.perform(get("/api/admin/payments").with(ADMIN).param("installments", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/payments").with(ADMIN).param("status", "PAGO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/payments").with(ADMIN).param("from", "31/01/2027"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void describeListsOnlyTheFiltersUsedWithoutPersonalData() {
        UUID clientId = UUID.fromString("c0000000-0000-4000-8000-000000000001");
        AdminPaymentFilter filter = new AdminPaymentFilter(LocalDate.of(2027, 1, 1), null, null,
                List.of(PaymentStatus.SUCCEEDED), null, null, clientId, null);

        assertThat(filter.describe())
                .isEqualTo("from=2027-01-01 status=[SUCCEEDED] clientId=c0000000-0000-4000-8000-000000000001");
    }

    @SuppressWarnings("unchecked")
    @Test
    void exportStreamsCsvAndAuditsTheFiltersUsed() throws Exception {
        doAnswer(invocation -> {
            ((Consumer<AdminPaymentResponse>) invocation.getArgument(1)).accept(payment("Ana Souza", "100.00"));
            return null;
        }).when(paymentService).forEachRow(any(), any());

        MvcResult started = mockMvc.perform(get("/api/admin/payments/export").with(ADMIN)
                        .param("format", "csv").param("status", "SUCCEEDED"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult result = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"pagamentos-20270115-1005.csv\""))
                .andReturn();

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Ana Souza");
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(any(), entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.EXPORT);
        assertThat(entry.getValue().details()).isEqualTo("format=csv status=[SUCCEEDED]");
    }

    @Test
    void exportRejectsUnknownFormatAndInvertedPeriodBeforeStreaming() throws Exception {
        mockMvc.perform(get("/api/admin/payments/export").with(ADMIN).param("format", "pdf"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/payments/export").with(ADMIN).param("format", "xlsx")
                        .param("from", "2027-02-01").param("to", "2027-01-01"))
                .andExpect(status().isBadRequest());

        verify(auditService, never()).record(any(), any());
        verify(paymentService, never()).forEachRow(any(), any());
    }

    @Test
    void regularClientCannotExport() throws Exception {
        mockMvc.perform(get("/api/admin/payments/export").param("format", "csv")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        verify(auditService, never()).record(any(), eq(null));
    }

    @Test
    void streamSendsReadyAndThenEachPaymentEvent() throws Exception {
        MvcResult started = mockMvc.perform(get("/api/admin/payments/stream").with(ADMIN))
                .andExpect(request().asyncStarted())
                .andReturn();
        UUID paymentId = UUID.randomUUID();

        feed.broadcast(new PaymentFeedEvent("PaymentConfirmed", paymentId, UUID.randomUUID(),
                Instant.parse("2027-01-10T15:00:00Z")));

        String body = started.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(started.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(body).contains("event:ready").contains("event:payment").contains("id:" + paymentId)
                .contains("\"eventType\":\"PaymentConfirmed\"");
        assertThat(body).doesNotContain("@");
    }
}
