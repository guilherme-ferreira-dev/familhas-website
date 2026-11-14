package br.com.familhas.recommendation;

import static br.com.familhas.trip.TripFixtures.booking;
import static br.com.familhas.trip.TripFixtures.category;
import static br.com.familhas.trip.TripFixtures.city;
import static br.com.familhas.trip.TripFixtures.client;
import static br.com.familhas.trip.TripFixtures.travelPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import br.com.familhas.booking.BookingRepository;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.category.CategoryEntity;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.rating.RatingRepository;
import br.com.familhas.recommendation.dto.RecommendationResponse;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;
import br.com.familhas.trip.TripService;

class RuleBasedRecommendationServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final RatingRepository ratings = mock(RatingRepository.class);
    private final TravelPackageRepository packages = mock(TravelPackageRepository.class);
    private final RecommendationService service = new RuleBasedRecommendationService(bookings, clients, ratings,
            packages, Clock.fixed(NOW, ZoneOffset.UTC));

    private ClientEntity ana;
    private CategoryEntity beach;
    private TravelPackageEntity maceio;
    private TravelPackageEntity natal;
    private TravelPackageEntity santiago;

    @BeforeEach
    void setUp() {
        ana = client();
        beach = category("Praia");
        maceio = travelPackage("Maceio", beach, city("Maceio", "Brasil"), LocalDateTime.of(2027, 3, 10, 6, 0));
        natal = travelPackage("Natal", beach, city("Natal", "Brasil"), LocalDateTime.of(2027, 1, 15, 6, 0));
        santiago = travelPackage("Santiago", category("Neve"), city("Santiago", "Chile"),
                LocalDateTime.of(2027, 7, 1, 6, 0));
        santiago.setPricePromotion(new BigDecimal("2500.00"));
        when(clients.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(ratings.findByClient_Id(ana.getId())).thenReturn(List.of());
        when(ratings.findPackageStats()).thenReturn(List.of());
        when(bookings.findBookedPackageIds(ana.getId())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private void catalog(TravelPackageEntity... available) {
        when(packages.findAll(any(Specification.class))).thenReturn(List.of(available));
    }

    @Test
    void recommendsSimilarPackagesWithReasonsAndSkipsTheOneAlreadyTraveled() {
        catalog(maceio, natal, santiago);
        when(bookings.findByClientIdAndStatusIn(ana.getId(), TripService.TRIP_STATUSES)).thenReturn(List.of(
                booking(ana, maceio, BookingStatus.COMPLETED, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12))));
        when(bookings.findBookedPackageIds(ana.getId())).thenReturn(List.of(maceio.getId()));

        List<RecommendationResponse> result = service.recommend(ana.getId(), 6);

        assertThat(result).extracting(r -> r.travelPackage().title()).containsExactly("Natal", "Santiago");
        assertThat(result.getFirst().reasons()).containsExactly(RecommendationReason.SIMILAR_CATEGORY,
                RecommendationReason.SIMILAR_DESTINATION, RecommendationReason.SEASONAL);
        assertThat(result.get(1).reasons()).containsExactly(RecommendationReason.ON_PROMOTION);
        assertThat(result.get(1).travelPackage().pricePerPerson()).isEqualByComparingTo("2500.00");
    }

    @Test
    void favoritesAreSignalsButNotSuggestions() {
        catalog(maceio, natal, santiago);
        ana.getFavorites().add(maceio);
        when(bookings.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of());

        assertThat(service.recommend(ana.getId(), 6)).extracting(r -> r.travelPackage().title())
                .containsExactly("Natal", "Santiago");
    }

    @Test
    void limitIsClampedAndEmptyCatalogGivesEmptyList() {
        catalog(maceio, natal, santiago);
        when(bookings.findByClientIdAndStatusIn(any(), any())).thenReturn(List.of());
        assertThat(service.recommend(ana.getId(), 1)).hasSize(1);
        assertThat(service.recommend(ana.getId(), 0)).hasSize(1);

        catalog();
        assertThat(service.recommend(ana.getId(), 6)).isEmpty();
    }
}
