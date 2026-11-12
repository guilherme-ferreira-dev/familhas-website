package br.com.familhas.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.dto.BookingResponse;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class BookingControllerTests {

    private static final UUID CLIENT = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    private static final UUID QUOTE = UUID.fromString("0a0e0000-0000-4000-8000-000000000001");
    private static final UUID BOOKING = UUID.fromString("b00c0000-0000-4000-8000-000000000001");
    private static final String BODY = "{\"quoteId\":\"" + QUOTE + "\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BookingService bookingService;
    @MockitoBean private KeycloakClientSyncService keycloakClientSyncService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject("sub-1").claim("email", "ana@exemplo.com"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Test
    void creatingBookingRequiresToken() throws Exception {
        mockMvc.perform(post("/api/me/bookings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(bookingService, never()).create(any(), any());
    }

    @Test
    void createReturns201WithLocationForTheClientFromTheToken() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(bookingService.create(eq(CLIENT), any())).thenReturn(new BookingResponse(BOOKING,
                BookingStatus.PENDING_PAYMENT, QUOTE, null, 2, List.of(), "BRL", new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), Instant.parse("2026-11-12T12:30:00Z"),
                null, null, null, null, Instant.parse("2026-11-12T12:00:00Z")));

        mockMvc.perform(withUser(post("/api/me/bookings")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/me/bookings/" + BOOKING))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.total").value(100.00))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void missingQuoteIdIsValidationProblem() throws Exception {
        mockMvc.perform(withUser(post("/api/me/bookings")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quoteId").exists());
    }

    @Test
    void alreadyBookedQuoteIsProblemWithCodeAndBookingId() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(bookingService.create(eq(CLIENT), any())).thenThrow(new BusinessException(HttpStatus.CONFLICT,
                "QUOTE_ALREADY_BOOKED", "Esta cotacao ja virou reserva.", Map.of("bookingId", BOOKING)));

        mockMvc.perform(withUser(post("/api/me/bookings")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_BOOKED"))
                .andExpect(jsonPath("$.bookingId").value(BOOKING.toString()))
                .andExpect(jsonPath("$.instance").value("/api/me/bookings"));
    }

    @Test
    void bookingOfAnotherClientIs404() throws Exception {
        when(keycloakClientSyncService.syncClientId(any())).thenReturn(CLIENT);
        when(bookingService.findForClient(CLIENT, BOOKING)).thenThrow(BookingService.notFound(BOOKING));

        mockMvc.perform(withUser(get("/api/me/bookings/" + BOOKING)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
