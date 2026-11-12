package br.com.familhas.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.familhas.provider.car.CarRentalOffer;
import br.com.familhas.provider.car.CarRentalSearchCriteria;
import br.com.familhas.provider.car.MockCarRentalProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightSearchCriteria;
import br.com.familhas.provider.flight.MockFlightProvider;
import br.com.familhas.provider.hotel.HotelOffer;
import br.com.familhas.provider.hotel.HotelSearchCriteria;
import br.com.familhas.provider.hotel.MockHotelProvider;

class MockProvidersTests {

    private static final UUID SAO_PAULO = UUID.fromString("c1770000-0000-4000-8000-000000000001");
    private static final UUID MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");
    private static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);

    @Test
    void mockFlightsAreDeterministicAndRebuiltFromOfferId() {
        MockFlightProvider provider = new MockFlightProvider();
        FlightSearchCriteria criteria = new FlightSearchCriteria(SAO_PAULO, MACEIO, DEPARTURE, null, 2);

        List<FlightOffer> first = provider.search(criteria);
        List<FlightOffer> second = provider.search(criteria);

        assertThat(first).hasSize(2).isEqualTo(second);
        assertThat(first).allSatisfy(offer -> {
            assertThat(offer.offerId()).startsWith("mock:");
            assertThat(offer.companyName()).contains("simulado");
            assertThat(offer.departureAt().toLocalDate()).isEqualTo(DEPARTURE);
            assertThat(offer.returnAt().toLocalDate()).isEqualTo(DEPARTURE.plusDays(7));
            assertThat(provider.findOffer(offer.offerId())).contains(offer);
        });
        assertThat(provider.search(new FlightSearchCriteria(SAO_PAULO, MACEIO, DEPARTURE, null, 10))).isEmpty();
    }

    @Test
    void mockHotelsAndCarsRoundTripThroughOfferId() {
        MockHotelProvider hotels = new MockHotelProvider();
        MockCarRentalProvider cars = new MockCarRentalProvider();

        List<HotelOffer> rooms = hotels.search(new HotelSearchCriteria(MACEIO, DEPARTURE, DEPARTURE.plusDays(3), 2));
        List<CarRentalOffer> carOffers = cars.search(
                new CarRentalSearchCriteria(MACEIO, DEPARTURE, DEPARTURE.plusDays(3), 4));

        assertThat(rooms).hasSize(2).allSatisfy(room -> assertThat(hotels.findOffer(room.offerId())).contains(room));
        assertThat(carOffers).hasSize(1)
                .allSatisfy(car -> assertThat(cars.findOffer(car.offerId())).contains(car));
        assertThat(cars.search(new CarRentalSearchCriteria(MACEIO, DEPARTURE, DEPARTURE.plusDays(3), 7))).isEmpty();
    }

    @Test
    void foreignOrBrokenOfferIdsAreNotFound() {
        assertThat(new MockFlightProvider().findOffer("curated:" + UUID.randomUUID())).isEmpty();
        assertThat(new MockFlightProvider().findOffer("mock:%%%")).isEmpty();
        assertThat(new MockHotelProvider().findOffer("mock:" + "bm90LXV1aWR8MA")).isEmpty();
    }
}
