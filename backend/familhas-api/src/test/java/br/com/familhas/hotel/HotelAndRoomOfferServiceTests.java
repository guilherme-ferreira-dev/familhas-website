package br.com.familhas.hotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.city.CityEntity;
import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.hotel.dto.HotelRequest;
import br.com.familhas.hotel.dto.RoomOfferRequest;
import br.com.familhas.hotel.dto.RoomOfferResponse;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class HotelAndRoomOfferServiceTests {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomOfferRepository roomOfferRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private HotelService hotelService;

    @Test
    void hotelCreateReturnsNotFoundForUnknownCity() {
        UUID cityId = UUID.randomUUID();
        when(entityManager.find(CityEntity.class, cityId)).thenReturn(null);

        assertThatThrownBy(() -> hotelService.create(
                new HotelRequest("Hotel", cityId, null, 4, null, null, EntityStatus.ACTIVE)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(hotelRepository, never()).save(any());
    }

    @Test
    void roomOfferCreateNormalizesCurrencyAndLinksHotel() {
        RoomOfferService roomOfferService = new RoomOfferService(roomOfferRepository, entityManager);
        UUID hotelId = UUID.randomUUID();
        when(entityManager.find(HotelEntity.class, hotelId)).thenReturn(HotelEntity.builder().id(hotelId).build());
        when(roomOfferRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomOfferResponse response = roomOfferService.create(new RoomOfferRequest(
                hotelId, " Standard ", BoardType.BREAKFAST, 2, new BigDecimal("500.00"), null, EntityStatus.ACTIVE));

        assertThat(response.hotelId()).isEqualTo(hotelId);
        assertThat(response.roomType()).isEqualTo("Standard");
        assertThat(response.currency()).isEqualTo("BRL");
    }
}
