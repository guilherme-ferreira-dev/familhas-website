package br.com.familhas.admin.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.quote.QuoteOrigin;
import br.com.familhas.quote.QuoteStatus;

@WebMvcTest(AdminQuoteController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminQuoteControllerTests {

    static final RequestPostProcessor ADMIN = jwt()
            .jwt(token -> token.subject("kc-admin").claim("preferred_username", "admin.familhas"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    static final String CLIENT = "c0000000-0000-4000-8000-000000000001";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminQuoteService quoteService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void createsAManualQuoteOnBehalfOfTheAdminInTheToken() throws Exception {
        mockMvc.perform(post("/api/admin/quotes").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content("""
                        {"clientId":"%s","passengers":2,"travelPackageId":"bac00000-0000-4000-8000-000000000001",
                         "manualDiscount":150.00,"validityHours":72,"internalNote":"Negociado no WhatsApp"}
                        """.formatted(CLIENT)))
                .andExpect(status().isCreated());

        ArgumentCaptor<ManualQuoteRequest> request = ArgumentCaptor.forClass(ManualQuoteRequest.class);
        ArgumentCaptor<AuditActor> actor = ArgumentCaptor.forClass(AuditActor.class);
        verify(quoteService).create(request.capture(), actor.capture());
        assertThat(request.getValue().manualDiscount()).isEqualByComparingTo("150.00");
        assertThat(request.getValue().validityHours()).isEqualTo(72);
        assertThat(actor.getValue()).isEqualTo(new AuditActor("kc-admin", "admin.familhas"));
    }

    @Test
    void validatesTheAdminTerms() throws Exception {
        for (String body : List.of(
                "{\"passengers\":2}",
                "{\"clientId\":\"%s\",\"passengers\":10}".formatted(CLIENT),
                "{\"clientId\":\"%s\",\"passengers\":2,\"manualDiscount\":-1}".formatted(CLIENT),
                "{\"clientId\":\"%s\",\"passengers\":2,\"manualDiscount\":10.001}".formatted(CLIENT),
                "{\"clientId\":\"%s\",\"passengers\":2,\"validityHours\":0}".formatted(CLIENT),
                "{\"clientId\":\"%s\",\"passengers\":2,\"internalNote\":\"%s\"}".formatted(CLIENT, "x".repeat(501)))) {
            mockMvc.perform(post("/api/admin/quotes").with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(quoteService, never()).create(any(), any());
    }

    @Test
    void belowCostIsUnprocessableWithAStableCode() throws Exception {
        when(quoteService.create(any(), any())).thenThrow(new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "QUOTE_BELOW_COST", "abaixo do custo", Map.of("maxManualDiscount", new BigDecimal("482.95"))));

        mockMvc.perform(post("/api/admin/quotes").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"passengers\":2,\"manualDiscount\":900}".formatted(CLIENT)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QUOTE_BELOW_COST"))
                .andExpect(jsonPath("$.maxManualDiscount").value(482.95));
    }

    @Test
    void listBindsOriginAndStatus() throws Exception {
        when(quoteService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/quotes").with(ADMIN).param("origin", "ADMIN").param("status", "EXPIRED"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminQuoteFilter> filter = ArgumentCaptor.forClass(AdminQuoteFilter.class);
        verify(quoteService).findAll(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new AdminQuoteFilter(QuoteOrigin.ADMIN, QuoteStatus.EXPIRED, null));
    }
}
