package br.com.familhas.travelPackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.carRental.CarRentalOfferEntity;
import br.com.familhas.category.CategoryEntity;
import br.com.familhas.city.CityEntity;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.hotel.RoomOfferEntity;
import br.com.familhas.travelPackage.dto.TravelPackageComponentRequest;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageRequest;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class TravelPackageServiceTests {

    private static final UUID FLIGHT_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final UUID ORIGIN_ID = UUID.randomUUID();
    private static final UUID DESTINATION_ID = UUID.randomUUID();
    private static final UUID ROOM_OFFER_ID = UUID.randomUUID();
    private static final UUID CAR_OFFER_ID = UUID.randomUUID();

    @Mock
    private TravelPackageRepository travelPackageRepository;

    @Mock
    private EntityManager entityManager;

    private TravelPackageService service;

    @BeforeEach
    void setUp() {
        service = new TravelPackageService(travelPackageRepository, entityManager);
    }

    @Test
    void createDefaultsCurrencyToBrlAndAddsComponents() {
        stubReferences();
        when(entityManager.find(RoomOfferEntity.class, ROOM_OFFER_ID))
                .thenReturn(RoomOfferEntity.builder().id(ROOM_OFFER_ID).build());
        when(entityManager.find(CarRentalOfferEntity.class, CAR_OFFER_ID))
                .thenReturn(CarRentalOfferEntity.builder().id(CAR_OFFER_ID).build());
        when(travelPackageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravelPackageResponse response = service.create(request(null, List.of(
                new TravelPackageComponentRequest(PackageComponentType.HOTEL_STAY, ROOM_OFFER_ID, null, 7),
                new TravelPackageComponentRequest(PackageComponentType.CAR_RENTAL, null, CAR_OFFER_ID, 3))));

        assertThat(response.currency()).isEqualTo("BRL");
        assertThat(response.components()).hasSize(2);
        assertThat(response.components().get(0).roomOfferId()).isEqualTo(ROOM_OFFER_ID);
        assertThat(response.components().get(1).carRentalOfferId()).isEqualTo(CAR_OFFER_ID);
        assertThat(response.components().get(1).quantity()).isEqualTo(3);
    }

    @Test
    void createRejectsHotelComponentWithoutRoomOffer() {
        stubReferences();

        assertThatThrownBy(() -> service.create(request("BRL", List.of(
                new TravelPackageComponentRequest(PackageComponentType.HOTEL_STAY, null, CAR_OFFER_ID, 7)))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(travelPackageRepository, never()).save(any());
    }

    @Test
    void createReturnsNotFoundForUnknownRoomOffer() {
        stubReferences();
        when(entityManager.find(RoomOfferEntity.class, ROOM_OFFER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.create(request("BRL", List.of(
                new TravelPackageComponentRequest(PackageComponentType.HOTEL_STAY, ROOM_OFFER_ID, null, 7)))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateWithoutComponentsKeepsExistingOnesAndCurrency() {
        UUID id = UUID.randomUUID();
        TravelPackageEntity existing = new TravelPackageEntity();
        existing.setId(id);
        existing.setCurrency("USD");
        TravelPackageComponentEntity component = TravelPackageComponentEntity.builder()
                .componentType(PackageComponentType.CAR_RENTAL)
                .carRentalOffer(CarRentalOfferEntity.builder().id(CAR_OFFER_ID).build())
                .quantity(2)
                .build();
        existing.getComponents().add(component);
        when(travelPackageRepository.findById(id)).thenReturn(Optional.of(existing));
        stubReferences();
        when(travelPackageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TravelPackageResponse response = service.update(id, request(null, null));

        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.components()).hasSize(1);
        assertThat(existing.getComponents()).containsExactly(component);
    }

    @Test
    void updateWithEmptyComponentsRemovesAllKeepingSameListInstance() {
        UUID id = UUID.randomUUID();
        TravelPackageEntity existing = new TravelPackageEntity();
        existing.getComponents().add(TravelPackageComponentEntity.builder()
                .componentType(PackageComponentType.CAR_RENTAL).quantity(2).build());
        List<TravelPackageComponentEntity> sameList = existing.getComponents();
        when(travelPackageRepository.findById(id)).thenReturn(Optional.of(existing));
        stubReferences();
        when(travelPackageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(id, request("BRL", List.of()));

        assertThat(existing.getComponents()).isEmpty();
        // A mesma instância precisa ser mantida por causa do orphanRemoval.
        assertThat(existing.getComponents()).isSameAs(sameList);
    }

    @Test
    void deleteRemovesAndFlushesSoForeignKeyErrorsSurfaceImmediately() {
        UUID id = UUID.randomUUID();
        TravelPackageEntity existing = new TravelPackageEntity();
        when(travelPackageRepository.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        InOrder order = inOrder(travelPackageRepository);
        order.verify(travelPackageRepository).delete(existing);
        order.verify(travelPackageRepository).flush();
    }

    @Test
    void findAllRejectsUnknownSortProperty() {
        assertThatThrownBy(() -> service.findAll(
                TravelPackageFilter.empty(), PageRequest.of(0, 20, Sort.by("flight.boardingAirport"))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllRejectsInvertedPriceRange() {
        TravelPackageFilter filter = new TravelPackageFilter(
                null, null, null, null, new BigDecimal("5000"), new BigDecimal("1000"), null, null);

        assertThatThrownBy(() -> service.findAll(filter, PageRequest.of(0, 20)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void stubReferences() {
        when(entityManager.find(FlightEntity.class, FLIGHT_ID)).thenReturn(FlightEntity.builder().id(FLIGHT_ID).build());
        when(entityManager.find(CategoryEntity.class, CATEGORY_ID))
                .thenReturn(CategoryEntity.builder().id(CATEGORY_ID).build());
        when(entityManager.find(CityEntity.class, ORIGIN_ID)).thenReturn(CityEntity.builder().id(ORIGIN_ID).build());
        when(entityManager.find(CityEntity.class, DESTINATION_ID))
                .thenReturn(CityEntity.builder().id(DESTINATION_ID).build());
    }

    private static TravelPackageRequest request(String currency, List<TravelPackageComponentRequest> components) {
        return new TravelPackageRequest(
                "Pacote teste", new BigDecimal("3000.00"), new BigDecimal("2500.00"), currency,
                EntityStatus.ACTIVE, null, FLIGHT_ID, CATEGORY_ID, ORIGIN_ID, DESTINATION_ID, components);
    }
}
