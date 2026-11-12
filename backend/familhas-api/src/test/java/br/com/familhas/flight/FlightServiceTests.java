package br.com.familhas.flight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.city.CityEntity;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.dto.FlightRequest;
import br.com.familhas.flight.dto.FlightResponse;
import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class FlightServiceTests {

    private static final UUID SAO_PAULO = UUID.fromString("c1770000-0000-4000-8000-000000000001");
    private static final UUID MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");

    @Mock
    private FlightRepository flightRepository;
    @Mock
    private EntityManager entityManager;

    private FlightService service;

    @BeforeEach
    void setUp() {
        service = new FlightService(flightRepository, entityManager);
    }

    @Test
    void createsFlightWithRoutePriceSeatsAndDefaultCurrency() {
        when(entityManager.find(CityEntity.class, SAO_PAULO)).thenReturn(city(SAO_PAULO));
        when(entityManager.find(CityEntity.class, MACEIO)).thenReturn(city(MACEIO));
        when(flightRepository.save(any(FlightEntity.class))).thenAnswer(i -> i.getArgument(0));

        FlightResponse response = service.create(request(SAO_PAULO, MACEIO, null));

        assertThat(response.originCityId()).isEqualTo(SAO_PAULO);
        assertThat(response.destinationCityId()).isEqualTo(MACEIO);
        assertThat(response.pricePerPassenger()).isEqualByComparingTo("899.90");
        assertThat(response.currency()).isEqualTo("BRL");
        assertThat(response.seatsAvailable()).isEqualTo(120);
        assertThat(response.status()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void sameOriginAndDestinationIsBadRequest() {
        assertThatThrownBy(() -> service.create(request(SAO_PAULO, SAO_PAULO, "BRL")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(flightRepository, never()).save(any());
    }

    @Test
    void unknownCityIsNotFound() {
        when(entityManager.find(CityEntity.class, SAO_PAULO)).thenReturn(null);

        assertThatThrownBy(() -> service.create(request(SAO_PAULO, MACEIO, "BRL")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static FlightRequest request(UUID origin, UUID destination, String currency) {
        return new FlightRequest("Gol", LocalDateTime.of(2027, 3, 10, 6, 20), LocalDateTime.of(2027, 3, 17, 20, 45),
                "GRU", "MCZ", origin, destination, new BigDecimal("899.90"), currency, 120, EntityStatus.ACTIVE);
    }

    private static CityEntity city(UUID id) {
        CityEntity city = new CityEntity();
        city.setId(id);
        return city;
    }
}
