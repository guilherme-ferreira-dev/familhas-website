package br.com.familhas.admin.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.booking.BookingStatus;
import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;

@WebMvcTest(AdminBookingController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AdminBookingControllerTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminBookingService bookingService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void bindsFilters() throws Exception {
        when(bookingService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/bookings").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .param("status", "CONFIRMED").param("clientId", clientId.toString())
                        .param("from", "2027-02-01"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminBookingFilter> filter = ArgumentCaptor.forClass(AdminBookingFilter.class);
        verify(bookingService).findAll(filter.capture(), any());
        assertThat(filter.getValue())
                .isEqualTo(new AdminBookingFilter(BookingStatus.CONFIRMED, clientId, LocalDate.of(2027, 2, 1), null));
    }

    @Test
    void unknownBookingIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(bookingService.findById(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "nope"));

        mockMvc.perform(get("/api/admin/bookings/{id}", id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
