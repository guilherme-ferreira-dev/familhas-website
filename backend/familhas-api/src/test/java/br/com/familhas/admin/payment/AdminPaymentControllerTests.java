package br.com.familhas.admin.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.payment.PaymentMethod;
import br.com.familhas.payment.PaymentStatus;

@WebMvcTest(AdminPaymentController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminPaymentControllerTests {

    static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminPaymentService paymentService;
    @MockitoBean private JwtDecoder jwtDecoder;

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
}
