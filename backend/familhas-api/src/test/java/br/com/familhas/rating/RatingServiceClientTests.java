package br.com.familhas.rating;

import static br.com.familhas.trip.TripFixtures.category;
import static br.com.familhas.trip.TripFixtures.city;
import static br.com.familhas.trip.TripFixtures.client;
import static br.com.familhas.trip.TripFixtures.travelPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.rating.dto.ClientRatingRequest;
import br.com.familhas.rating.dto.MyRatingResponse;
import br.com.familhas.travelPackage.TravelPackageEntity;
import jakarta.persistence.EntityManager;

/** Regras das avaliações feitas pelo próprio cliente (/api/me/ratings; ADR 0010, D5). */
class RatingServiceClientTests {

    /** 13/11/2026 às 01:00 em São Paulo (ainda 13/11 em UTC às 04:00). */
    private static final Instant NOW = Instant.parse("2026-11-13T04:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    private final RatingRepository ratings = mock(RatingRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RatingService service = new RatingService(ratings, bookings, entityManager,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private ClientEntity ana;
    private TravelPackageEntity maceio;

    @BeforeEach
    void setUp() {
        ana = client();
        maceio = travelPackage("Maceio 7 noites", category("Praia"), city("Maceio", "Brasil"),
                LocalDateTime.of(2027, 3, 10, 6, 20));
        when(entityManager.find(ClientEntity.class, ana.getId())).thenReturn(ana);
        when(entityManager.find(TravelPackageEntity.class, maceio.getId())).thenReturn(maceio);
        when(ratings.save(any(RatingEntity.class))).thenAnswer(i -> {
            RatingEntity rating = i.getArgument(0);
            if (rating.getId() == null) {
                rating.setId(UUID.randomUUID());
            }
            return rating;
        });
    }

    @Test
    void ratesPackageOfAPastTripUsingTheBusinessDay() {
        when(bookings.existsPastTrip(ana.getId(), maceio.getId(), TODAY)).thenReturn(true);

        MyRatingResponse created = service.createForClient(ana.getId(), new ClientRatingRequest(5, maceio.getId()));

        assertThat(created.rating()).isEqualTo(5);
        assertThat(created.travelPackage().id()).isEqualTo(maceio.getId());
        assertThat(created.travelPackage().destinationCityName()).isEqualTo("Maceio");
    }

    @Test
    void packageWithoutPastTripCannotBeRated() {
        when(bookings.existsPastTrip(ana.getId(), maceio.getId(), TODAY)).thenReturn(false);

        assertThatThrownBy(() -> service.createForClient(ana.getId(), new ClientRatingRequest(5, maceio.getId())))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("RATING_NOT_ALLOWED");
                    assertThat(ex.getStatusCode().value()).isEqualTo(422);
                });
        verify(ratings, never()).save(any());
    }

    @Test
    void secondRatingOfTheSamePackageIsConflictWithCode() {
        when(bookings.existsPastTrip(ana.getId(), maceio.getId(), TODAY)).thenReturn(true);
        when(ratings.existsByClient_IdAndTravelPackage_Id(ana.getId(), maceio.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.createForClient(ana.getId(), new ClientRatingRequest(4, maceio.getId())))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("RATING_ALREADY_EXISTS");
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                });
    }

    @Test
    void changingOnlyTheScoreDoesNotRecheckTheTrip() {
        RatingEntity existing = new RatingEntity(UUID.randomUUID(), 3, ana, maceio);
        when(ratings.findById(existing.getId())).thenReturn(Optional.of(existing));

        MyRatingResponse updated = service.updateForClient(ana.getId(), existing.getId(),
                new ClientRatingRequest(5, maceio.getId()));

        assertThat(updated.rating()).isEqualTo(5);
        verify(bookings, never()).existsPastTrip(any(), any(), any());
    }

    @Test
    void movingARatingToAnotherPackageRequiresATripWithIt() {
        TravelPackageEntity other = travelPackage("Buenos Aires", category("Cidade"),
                city("Buenos Aires", "Argentina"), LocalDateTime.of(2027, 4, 3, 11, 10));
        RatingEntity existing = new RatingEntity(UUID.randomUUID(), 3, ana, maceio);
        when(ratings.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(bookings.existsPastTrip(eq(ana.getId()), eq(other.getId()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateForClient(ana.getId(), existing.getId(),
                new ClientRatingRequest(5, other.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("RATING_NOT_ALLOWED"));
        assertThat(existing.getTravelPackage()).isSameAs(maceio);
    }

    @Test
    void ratingOfAnotherClientIs404WithCodeOnUpdateAndDelete() {
        RatingEntity bruno = new RatingEntity(UUID.randomUUID(), 4, client(), maceio);
        when(ratings.findById(bruno.getId())).thenReturn(Optional.of(bruno));

        assertThatThrownBy(() -> service.updateForClient(ana.getId(), bruno.getId(),
                new ClientRatingRequest(1, maceio.getId())))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("RATING_NOT_FOUND"));
        assertThatThrownBy(() -> service.deleteForClient(ana.getId(), bruno.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("RATING_NOT_FOUND"));
        assertThat(bruno.getRating()).isEqualTo(4);
        verify(ratings, never()).delete(any(RatingEntity.class));
    }
}
