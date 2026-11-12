package br.com.familhas.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.common.Money;
import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.Transmission;
import br.com.familhas.pricing.PricingProperties;
import br.com.familhas.pricing.PricingService;
import br.com.familhas.provider.ProviderGateway;
import br.com.familhas.provider.ProviderResult;
import br.com.familhas.provider.car.CarRentalOffer;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.hotel.HotelOffer;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.search.dto.CarSearchParams;
import br.com.familhas.search.dto.CarSearchResult;
import br.com.familhas.search.dto.FlightSearchParams;
import br.com.familhas.search.dto.FlightSearchResult;
import br.com.familhas.search.dto.HotelSearchParams;
import br.com.familhas.search.dto.HotelSearchResult;
import br.com.familhas.search.dto.SearchPage;
import br.com.familhas.travelPackage.TravelPackageRepository;

class CatalogSearchServiceTests {

    private static final UUID SAO_PAULO = UUID.fromString("c1770000-0000-4000-8000-000000000001");
    private static final UUID MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");
    private static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("totalPrice"));

    private final ProviderGateway gateway = mock(ProviderGateway.class);
    private final List<FlightProvider> flightProviders = List.of(mock(FlightProvider.class));
    private final List<HotelProvider> hotelProviders = List.of(mock(HotelProvider.class));
    private final List<CarRentalProvider> carProviders = List.of(mock(CarRentalProvider.class));
    private CatalogSearchService service;

    @BeforeEach
    void setUp() {
        PricingService pricing = new PricingService(new PricingProperties(BigDecimal.TEN, BigDecimal.ZERO, "BRL"));
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC);
        service = new CatalogSearchService(gateway, flightProviders, hotelProviders, carProviders,
                mock(TravelPackageRepository.class), pricing, clock);
    }

    @Test
    void flightPricesIncludeMarginAndAreSortedByTotal() {
        when(gateway.search(eq(flightProviders), any())).thenReturn(new ProviderResult<>(List.of(
                flight("curated:a", "899.90"), flight("mock:b", "500.00")), List.of("hoteldo")));

        SearchPage<FlightSearchResult> page = service.searchFlights(flightParams(2, null, null), FIRST_PAGE);

        assertThat(page.content()).extracting(FlightSearchResult::offerId).containsExactly("mock:b", "curated:a");
        FlightSearchResult gol = page.content().get(1);
        assertThat(gol.pricePerPassenger()).isEqualByComparingTo("989.89");
        assertThat(gol.totalPrice()).isEqualByComparingTo("1979.78");
        assertThat(gol.passengers()).isEqualTo(2);
        assertThat(page.unavailableProviders()).containsExactly("hoteldo");
    }

    @Test
    void priceRangeAppliesToTheGroupTotal() {
        when(gateway.search(eq(flightProviders), any())).thenReturn(new ProviderResult<>(List.of(
                flight("curated:a", "899.90"), flight("mock:b", "500.00")), List.of()));

        SearchPage<FlightSearchResult> page = service.searchFlights(
                flightParams(2, new BigDecimal("1200"), new BigDecimal("1979.78")), FIRST_PAGE);

        assertThat(page.content()).extracting(FlightSearchResult::offerId).containsExactly("curated:a");
    }

    @Test
    void resultsArePaginatedInMemory() {
        when(gateway.search(eq(flightProviders), any())).thenReturn(new ProviderResult<>(List.of(
                flight("a:1", "100"), flight("a:2", "200"), flight("a:3", "300")), List.of()));

        SearchPage<FlightSearchResult> page = service.searchFlights(flightParams(1, null, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "totalPrice")));

        assertThat(page.content()).extracting(FlightSearchResult::offerId).containsExactly("a:1");
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "partida no passado, 2026-11-11, , c1770000-0000-4000-8000-000000000002, , ",
            "volta antes da ida, 2027-03-10, 2027-03-09, c1770000-0000-4000-8000-000000000002, , ",
            "mesma origem e destino, 2027-03-10, , c1770000-0000-4000-8000-000000000001, , ",
            "faixa de preco invertida, 2027-03-10, , c1770000-0000-4000-8000-000000000002, 500, 100" })
    void invalidFlightSearchIsBadRequest(String scenario, LocalDate departure, LocalDate returnDate,
            UUID destination, BigDecimal min, BigDecimal max) {
        FlightSearchParams params = new FlightSearchParams(SAO_PAULO, destination, departure, returnDate, 1, min,
                max);

        assertThatThrownBy(() -> service.searchFlights(params, FIRST_PAGE))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sortOutsideTheWhitelistIsBadRequest() {
        when(gateway.search(eq(flightProviders), any())).thenReturn(new ProviderResult<>(List.of(), List.of()));

        assertThatThrownBy(() -> service.searchFlights(flightParams(1, null, null),
                PageRequest.of(0, 20, Sort.by("pricePerPassenger"))))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void hotelGroupLargerThanRoomUsesMoreRoomsAndMinStarsFilters() {
        when(gateway.search(eq(hotelProviders), any())).thenReturn(new ProviderResult<>(List.of(
                hotel("curated:duplo", 5, 2, "890.00"), hotel("curated:simples", 2, 2, "100.00")), List.of()));

        SearchPage<HotelSearchResult> page = service.searchHotels(new HotelSearchParams(MACEIO, DEPARTURE,
                DEPARTURE.plusDays(7), 3, 4, null, null), FIRST_PAGE);

        assertThat(page.content()).singleElement().satisfies(room -> {
            assertThat(room.rooms()).isEqualTo(2);
            assertThat(room.nights()).isEqualTo(7);
            // 890 x 2 quartos x 7 noites = 12460 + 10% = 13706
            assertThat(room.totalPrice()).isEqualByComparingTo("13706.00");
            assertThat(room.pricePerNight()).isEqualByComparingTo("979.00");
        });
    }

    @ParameterizedTest
    @CsvSource({ "2027-03-10, 2027-03-10", "2027-03-10, 2027-04-10" })
    void hotelStayMustBeOneToThirtyNights(LocalDate checkIn, LocalDate checkOut) {
        assertThatThrownBy(() -> service.searchHotels(new HotelSearchParams(MACEIO, checkIn, checkOut, 2, null,
                null, null), FIRST_PAGE)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sameDayCarRentalCountsOneDayAndCategoryFilters() {
        when(gateway.search(eq(carProviders), any())).thenReturn(new ProviderResult<>(List.of(
                car("curated:argo", CarCategory.ECONOMY, "119.90"), car("curated:suv", CarCategory.SUV, "300")),
                List.of()));

        SearchPage<CarSearchResult> page = service.searchCars(new CarSearchParams(MACEIO, DEPARTURE, DEPARTURE, 4,
                CarCategory.ECONOMY, null, null), FIRST_PAGE);

        assertThat(page.content()).singleElement().satisfies(car -> {
            assertThat(car.days()).isEqualTo(1);
            assertThat(car.totalPrice()).isEqualByComparingTo("131.89");
        });
    }

    @Test
    void roomsAreRoundedUp() {
        assertThat(CatalogSearchService.roomsFor(1, 2)).isEqualTo(1);
        assertThat(CatalogSearchService.roomsFor(2, 2)).isEqualTo(1);
        assertThat(CatalogSearchService.roomsFor(5, 2)).isEqualTo(3);
        assertThat(CatalogSearchService.roomsFor(6, 6)).isEqualTo(1);
    }

    private static FlightSearchParams flightParams(int passengers, BigDecimal min, BigDecimal max) {
        return new FlightSearchParams(SAO_PAULO, MACEIO, DEPARTURE, null, passengers, min, max);
    }

    private static FlightOffer flight(String offerId, String price) {
        return new FlightOffer(offerId, offerId.split(":")[0], "Cia", SAO_PAULO, MACEIO, DEPARTURE.atTime(6, 20),
                DEPARTURE.plusDays(7).atTime(20, 45), "GRU", "MCZ", Money.of(price, "BRL"), 100);
    }

    private static HotelOffer hotel(String offerId, int stars, int maxGuests, String price) {
        return new HotelOffer(offerId, "curated", "Hotel", MACEIO, stars, "Quarto", BoardType.BREAKFAST, maxGuests,
                Money.of(price, "BRL"));
    }

    private static CarRentalOffer car(String offerId, CarCategory category, String price) {
        return new CarRentalOffer(offerId, "curated", MACEIO, "Locadora", "Carro", category, Transmission.MANUAL, 5,
                Money.of(price, "BRL"));
    }
}
