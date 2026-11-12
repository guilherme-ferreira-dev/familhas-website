package br.com.familhas.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.provider.car.CarRentalOffer;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.car.CarRentalSearchCriteria;
import br.com.familhas.provider.flight.CuratedFlightProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.flight.FlightSearchCriteria;
import br.com.familhas.provider.hotel.HotelOffer;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.provider.hotel.HotelSearchCriteria;

/**
 * Adapters do catálogo curado e o gateway contra o Oracle real com o seed V2..V9.
 * O mock fica ligado para provar que os dois tipos de provedor convivem na mesma busca.
 */
@SpringBootTest(properties = {
        "familhas.outbox.publisher-enabled=false",
        "familhas.providers.mock.enabled=true" })
@Testcontainers(disabledWithoutDocker = true)
class CuratedProvidersIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID SAO_PAULO = UUID.fromString("c1770000-0000-4000-8000-000000000001");
    static final UUID MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");
    static final UUID BRASILIA = UUID.fromString("c1770000-0000-4000-8000-000000000006");
    static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");
    static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);

    @Autowired private ProviderGateway gateway;
    @Autowired private List<FlightProvider> flightProviders;
    @Autowired private List<HotelProvider> hotelProviders;
    @Autowired private List<CarRentalProvider> carProviders;

    @Test
    void curatedFlightsMatchRouteDayAndSeatsAlongsideMockOffers() {
        ProviderResult<FlightOffer> result = gateway.search(flightProviders,
                new FlightSearchCriteria(SAO_PAULO, MACEIO, DEPARTURE, null, 2));

        assertThat(result.partial()).isFalse();
        assertThat(result.offers()).filteredOn(o -> o.provider().equals("curated"))
                .singleElement()
                .satisfies(gol -> {
                    assertThat(gol.offerId()).isEqualTo(CuratedFlightProvider.offerIdOf(FLIGHT_GOL));
                    assertThat(gol.pricePerPassenger().amount()).isEqualByComparingTo("899.90");
                    assertThat(gol.returnAt().toLocalDate()).isEqualTo(LocalDate.of(2027, 3, 17));
                });
        assertThat(result.offers()).filteredOn(o -> o.provider().equals("mock")).hasSize(2);
    }

    @Test
    void flightWithoutEnoughSeatsOrOnAnotherDayIsNotOffered() {
        // Brasília -> Maceió tem só 4 assentos.
        assertThat(curatedFlights(new FlightSearchCriteria(BRASILIA, MACEIO, DEPARTURE, null, 4))).hasSize(1);
        assertThat(curatedFlights(new FlightSearchCriteria(BRASILIA, MACEIO, DEPARTURE, null, 5))).isEmpty();
        assertThat(curatedFlights(new FlightSearchCriteria(SAO_PAULO, MACEIO, DEPARTURE.plusDays(1), null, 1)))
                .isEmpty();
        assertThat(curatedFlights(new FlightSearchCriteria(SAO_PAULO, MACEIO, DEPARTURE, DEPARTURE.plusDays(3), 1)))
                .isEmpty();
    }

    @Test
    void curatedHotelsAndCarsInTheDestinationCity() {
        List<HotelOffer> rooms = gateway.search(hotelProviders,
                new HotelSearchCriteria(MACEIO, DEPARTURE, DEPARTURE.plusDays(7), 2)).offers();
        List<CarRentalOffer> cars = gateway.search(carProviders,
                new CarRentalSearchCriteria(MACEIO, DEPARTURE, DEPARTURE.plusDays(3), 4)).offers();

        assertThat(rooms).filteredOn(o -> o.provider().equals("curated"))
                .extracting(HotelOffer::roomType)
                .containsExactlyInAnyOrder("Standard Duplo", "Luxo Vista Mar Familia");
        assertThat(cars).filteredOn(o -> o.provider().equals("curated"))
                .extracting(CarRentalOffer::companyName)
                .containsExactly("Locadora Litoral");
    }

    @Test
    void findOfferReturnsCurrentPriceFromTheOwnerProvider() {
        FlightOffer gol = gateway.findOffer(flightProviders, CuratedFlightProvider.offerIdOf(FLIGHT_GOL))
                .orElseThrow();

        assertThat(gol.companyName()).isEqualTo("Gol Linhas Aereas");
        assertThat(gateway.findOffer(flightProviders, CuratedFlightProvider.offerIdOf(UUID.randomUUID())))
                .isEmpty();
    }

    private List<FlightOffer> curatedFlights(FlightSearchCriteria criteria) {
        return gateway.search(flightProviders, criteria).offers().stream()
                .filter(o -> o.provider().equals("curated"))
                .toList();
    }
}
