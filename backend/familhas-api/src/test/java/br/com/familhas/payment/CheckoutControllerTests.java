package br.com.familhas.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.payment.dto.CheckoutSessionResponse;

@WebMvcTest(CheckoutController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CheckoutControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    private static final UUID BOOKING = UUID.fromString("b00c0000-0000-4000-8000-000000000001");
    private static final UUID PAYMENT = UUID.fromString("9a7e0000-0000-4000-8000-000000000001");
    private static final String URL = "/api/me/bookings/" + BOOKING + "/checkout-session";
    private static final CheckoutSessionResponse SESSION = new CheckoutSessionResponse(PAYMENT,
            PaymentMethod.PIX, "https://checkout.stripe.com/c/pay/cs_test_1", Instant.parse("2026-11-12T12:30:00Z"));

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CheckoutService checkoutService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1").claim("email", "ana@exemplo.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void requiresToken() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isUnauthorized());
        verify(checkoutService, never()).open(any(), any(), any());
    }

    @Test
    void newSessionIs201() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(checkoutService.open(CLIENT, BOOKING, PaymentMethod.PIX))
                .thenReturn(new CheckoutService.CheckoutResult(SESSION, true));

        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT.toString()))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/c/pay/cs_test_1"));
    }

    @Test
    void reusedSessionIs200() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(checkoutService.open(eq(CLIENT), eq(BOOKING), any()))
                .thenReturn(new CheckoutService.CheckoutResult(SESSION, false));

        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownOrMissingMethodIs400() throws Exception {
        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.paymentMethod").exists());
        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"BOLETO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stripeUnavailableIs503() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(checkoutService.open(eq(CLIENT), eq(BOOKING), any()))
                .thenThrow(new PaymentGatewayException("Stripe nao configurado"));

        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CREDIT_CARD\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Pagamento indisponivel no momento. Tente novamente em instantes."));
    }

    @Test
    void installmentsAreNotAcceptedSilently() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(checkoutService.open(eq(CLIENT), eq(BOOKING), any()))
                .thenReturn(new CheckoutService.CheckoutResult(SESSION, true));

        // Campo desconhecido é ignorado pelo Jackson: o pagamento continua à vista (ADR 0008).
        mockMvc.perform(withUser(post(URL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CREDIT_CARD\",\"installments\":6}"))
                .andExpect(status().isCreated());
        verify(checkoutService).open(CLIENT, BOOKING, PaymentMethod.CREDIT_CARD);
    }
}
