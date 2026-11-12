package br.com.familhas.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.provider.ProviderType;
import br.com.familhas.provider.ProviderUnavailableException;
import br.com.familhas.search.dto.FlightSearchParams;
import br.com.familhas.search.dto.SearchPage;

@WebMvcTest(SearchController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class SearchControllerTests {

    private static final String SAO_PAULO = "c1770000-0000-4000-8000-000000000001";
    private static final String MACEIO = "c1770000-0000-4000-8000-000000000002";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CatalogSearchService searchService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void flightSearchIsPublicAndBindsParameters() throws Exception {
        when(searchService.searchFlights(any(), any()))
                .thenReturn(new SearchPage<>(List.of(), 0, 20, 0, 0, List.of("mock")));

        mockMvc.perform(get("/api/search/flights")
                        .param("originCityId", SAO_PAULO).param("destinationCityId", MACEIO)
                        .param("departureDate", "2027-03-10").param("passengers", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unavailableProviders[0]").value("mock"));

        ArgumentCaptor<FlightSearchParams> params = ArgumentCaptor.forClass(FlightSearchParams.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(searchService).searchFlights(params.capture(), pageable.capture());
        assertThat(params.getValue().passengersOrDefault()).isEqualTo(2);
        assertThat(pageable.getValue().getSort().getOrderFor("totalPrice")).isNotNull();
    }

    @Test
    void missingRequiredParameterIsProblemDetails400() throws Exception {
        mockMvc.perform(get("/api/search/flights").param("originCityId", SAO_PAULO))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.destinationCityId").exists())
                .andExpect(jsonPath("$.errors.departureDate").exists());
    }

    @Test
    void tooManyPassengersIsRejected() throws Exception {
        mockMvc.perform(get("/api/search/hotels").param("cityId", MACEIO).param("checkIn", "2027-03-10")
                        .param("checkOut", "2027-03-17").param("guests", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.guests").exists());
    }

    @Test
    void serviceValidationErrorsKeepProblemDetails() throws Exception {
        when(searchService.searchCars(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "dropOffDate must be on or after"));

        mockMvc.perform(get("/api/search/cars").param("cityId", MACEIO).param("pickUpDate", "2027-03-10")
                        .param("dropOffDate", "2027-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("dropOffDate must be on or after"));
    }

    @Test
    void unavailableProviderIs503() throws Exception {
        when(searchService.searchPackages(any(), any()))
                .thenThrow(new ProviderUnavailableException(ProviderType.HOTEL, "hoteldo"));

        mockMvc.perform(get("/api/search/packages"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.provider").value("hoteldo"));
    }
}
