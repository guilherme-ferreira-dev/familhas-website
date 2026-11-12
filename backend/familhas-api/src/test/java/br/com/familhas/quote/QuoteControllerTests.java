package br.com.familhas.quote;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.quote.dto.QuoteResponse;

@WebMvcTest(QuoteController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class QuoteControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    private static final UUID QUOTE = UUID.fromString("0a0e0000-0000-4000-8000-000000000001");
    private static final String BODY = """
            {"travelPackageId":"bac00000-0000-4000-8000-000000000001","passengers":2}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private QuoteService quoteService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1").claim("email", "ana@exemplo.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void creatingQuoteRequiresToken() throws Exception {
        mockMvc.perform(post("/api/me/quotes").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(quoteService, never()).create(any(), any());
    }

    @Test
    void createReturns201WithLocationForTheClientFromTheToken() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(quoteService.create(eq(CLIENT), any())).thenReturn(new QuoteResponse(QUOTE, QuoteStatus.OPEN, null, 2,
                List.of(), "BRL", new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), Instant.parse("2026-11-13T12:00:00Z"), null));

        mockMvc.perform(withUser(post("/api/me/quotes")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/me/quotes/" + QUOTE))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void invalidBodyIsValidationProblem() throws Exception {
        mockMvc.perform(withUser(post("/api/me/quotes")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passengers\":12,\"flight\":{\"offerId\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.passengers").exists())
                .andExpect(jsonPath("$['errors']['flight.offerId']").exists());
    }

    @Test
    void quoteOfAnotherClientIs404() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(quoteService.findForClient(CLIENT, QUOTE))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found: " + QUOTE));

        mockMvc.perform(withUser(get("/api/me/quotes/" + QUOTE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
