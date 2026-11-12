package br.com.familhas.payment.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.payment.InvalidWebhookException;
import br.com.familhas.payment.PaymentGateway;
import br.com.familhas.payment.PaymentNotification;

@WebMvcTest(StripeWebhookController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class StripeWebhookControllerTests {

    private static final String URL = "/api/webhooks/stripe";
    private static final String BODY = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentGateway gateway;
    @MockitoBean private StripeWebhookService webhookService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void signedEventIsAcceptedWithoutJwtAndTheRawBodyIsVerified() throws Exception {
        PaymentNotification notification = PaymentNotification.ignored("evt_1", "checkout.session.completed");
        when(gateway.parseWebhook(BODY, "t=1,v1=abc")).thenReturn(notification);

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=abc")
                        .content(BODY))
                .andExpect(status().isOk());
        verify(webhookService).handle(notification);
    }

    @Test
    void invalidSignatureIs400AndNothingIsProcessed() throws Exception {
        when(gateway.parseWebhook(any(), any())).thenThrow(new InvalidWebhookException("Assinatura do webhook invalida"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=x")
                        .content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Webhook invalido."));
        verify(webhookService, never()).handle(any());
    }

    @Test
    void sameEventDeliveredInParallelIsStill200() throws Exception {
        PaymentNotification notification = PaymentNotification.ignored("evt_1", "checkout.session.completed");
        when(gateway.parseWebhook(eq(BODY), any())).thenReturn(notification);
        when(webhookService.handle(notification)).thenThrow(new DataIntegrityViolationException("pk"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=abc")
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void processingFailureIs500SoStripeRetries() throws Exception {
        PaymentNotification notification = PaymentNotification.ignored("evt_1", "checkout.session.completed");
        when(gateway.parseWebhook(eq(BODY), any())).thenReturn(notification);
        when(webhookService.handle(notification)).thenThrow(new IllegalStateException("banco fora"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=abc")
                        .content(BODY))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void onlyPostIsPublic() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/webhooks/outro").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
