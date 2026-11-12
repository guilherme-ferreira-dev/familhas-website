package br.com.familhas.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.familhas.carRental.CarRentalOfferService;
import br.com.familhas.category.CategoryService;
import br.com.familhas.city.CityService;
import br.com.familhas.common.PageResponse;
import br.com.familhas.config.RestAccessDeniedHandler;
import br.com.familhas.config.RestAuthenticationEntryPoint;
import br.com.familhas.config.SecurityConfig;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.FlightService;
import br.com.familhas.flight.dto.FlightFilter;
import br.com.familhas.hotel.HotelService;
import br.com.familhas.hotel.RoomOfferService;
import br.com.familhas.hotel.dto.HotelResponse;
import br.com.familhas.travelPackage.TravelPackageService;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

@WebMvcTest(CatalogController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class CatalogControllerTests {

    private static final UUID HOTEL_ID = UUID.fromString("ee1e0000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelPackageService travelPackageService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private CityService cityService;
    @MockitoBean
    private FlightService flightService;
    @MockitoBean
    private HotelService hotelService;
    @MockitoBean
    private RoomOfferService roomOfferService;
    @MockitoBean
    private CarRentalOfferService carRentalOfferService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void flightsArePublicFilteredByRouteAndAlwaysActive() throws Exception {
        when(flightService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        UUID origin = UUID.fromString("c1770000-0000-4000-8000-000000000001");

        mockMvc.perform(get("/api/flights").param("status", "INACTIVE").param("originCityId", origin.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<FlightFilter> filter = ArgumentCaptor.forClass(FlightFilter.class);
        verify(flightService).findAll(filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().status()).isEqualTo(EntityStatus.ACTIVE);
        assertThat(filter.getValue().originCityId()).isEqualTo(origin);
    }

    @Test
    void packagesArePublicAndAlwaysFilteredByActive() throws Exception {
        when(travelPackageService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        // Mesmo pedindo INACTIVE, o catalogo publico forca ACTIVE.
        mockMvc.perform(get("/api/travel-packages").param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        ArgumentCaptor<TravelPackageFilter> filter = ArgumentCaptor.forClass(TravelPackageFilter.class);
        verify(travelPackageService).findAll(filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().status()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void packagesKeepOtherFilters() throws Exception {
        when(travelPackageService.findAll(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/travel-packages")
                .param("destinationCityId", "c1770000-0000-4000-8000-000000000002")
                .param("maxPrice", "3000")).andExpect(status().isOk());

        ArgumentCaptor<TravelPackageFilter> filter = ArgumentCaptor.forClass(TravelPackageFilter.class);
        verify(travelPackageService).findAll(filter.capture(), any(Pageable.class));
        assertThat(filter.getValue().status()).isEqualTo(EntityStatus.ACTIVE);
        assertThat(filter.getValue().maxPrice()).isEqualByComparingTo("3000");
        assertThat(filter.getValue().destinationCityId())
                .isEqualTo(UUID.fromString("c1770000-0000-4000-8000-000000000002"));
    }

    @Test
    void inactiveHotelLooksLikeNotFound() throws Exception {
        when(hotelService.findById(HOTEL_ID)).thenReturn(new HotelResponse(
                HOTEL_ID, "Antigo", UUID.randomUUID(), null, 3, null, null, EntityStatus.INACTIVE));

        mockMvc.perform(get("/api/hotels/{id}", HOTEL_ID)).andExpect(status().isNotFound());
    }

    @Test
    void activeHotelIsReturned() throws Exception {
        when(hotelService.findById(HOTEL_ID)).thenReturn(new HotelResponse(
                HOTEL_ID, "Pajucara", UUID.randomUUID(), null, 5, null, null, EntityStatus.ACTIVE));

        mockMvc.perform(get("/api/hotels/{id}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pajucara"));
    }

    @Test
    void writingToCatalogIsNotAllowedForAnonymous() throws Exception {
        mockMvc.perform(post("/api/travel-packages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
