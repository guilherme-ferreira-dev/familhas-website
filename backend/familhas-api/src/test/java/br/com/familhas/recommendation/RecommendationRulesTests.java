package br.com.familhas.recommendation;

import static br.com.familhas.recommendation.RecommendationReason.HIGHLY_RATED;
import static br.com.familhas.recommendation.RecommendationReason.ON_PROMOTION;
import static br.com.familhas.recommendation.RecommendationReason.SEASONAL;
import static br.com.familhas.recommendation.RecommendationReason.SIMILAR_CATEGORY;
import static br.com.familhas.recommendation.RecommendationReason.SIMILAR_DESTINATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import br.com.familhas.recommendation.RecommendationRules.Candidate;
import br.com.familhas.recommendation.RecommendationRules.Profile;
import br.com.familhas.recommendation.RecommendationRules.Scored;

class RecommendationRulesTests {

    private static final UUID BEACH = UUID.fromString("ca7e0000-0000-4000-8000-000000000001");
    private static final UUID CITY = UUID.fromString("ca7e0000-0000-4000-8000-000000000002");
    private static final UUID SNOW = UUID.fromString("ca7e0000-0000-4000-8000-000000000003");

    private static Candidate candidate(UUID category, String country, int month, boolean promotion, Double average) {
        return new Candidate(UUID.randomUUID(), category, country, LocalDateTime.of(2027, month, 10, 8, 0), promotion,
                average);
    }

    static Stream<Arguments> singleSignals() {
        UUID traveled = UUID.randomUUID();
        return Stream.of(
                Arguments.of("viagem na categoria", new Profile().addTrip(traveled, BEACH, "Peru", null),
                        candidate(BEACH, "Chile", 6, false, null), 3.0, List.of(SIMILAR_CATEGORY)),
                Arguments.of("favorito na categoria", new Profile().addFavorite(UUID.randomUUID(), BEACH, "Peru"),
                        candidate(BEACH, "Chile", 6, false, null), 2.0, List.of(SIMILAR_CATEGORY)),
                Arguments.of("avaliação boa", new Profile().addRating(UUID.randomUUID(), BEACH, "Peru", 4),
                        candidate(BEACH, "Chile", 6, false, null), 2.0, List.of(SIMILAR_CATEGORY)),
                Arguments.of("avaliação ruim afasta a categoria",
                        new Profile().addRating(UUID.randomUUID(), BEACH, "Peru", 2),
                        candidate(BEACH, "Chile", 6, false, null), -3.0, List.of()),
                Arguments.of("avaliação neutra não conta", new Profile().addRating(UUID.randomUUID(), BEACH, "Peru", 3),
                        candidate(BEACH, "Chile", 6, false, null), 0.0, List.of()),
                Arguments.of("mesmo país (sem acento e caixa)",
                        new Profile().addFavorite(UUID.randomUUID(), CITY, "Brasil"),
                        candidate(SNOW, "BRASÍL", 6, false, null), 2.0, List.of(SIMILAR_DESTINATION)),
                Arguments.of("época de viagem", new Profile().addTrip(null, null, null, Month.JULY),
                        candidate(SNOW, "Chile", 6, false, null), 1.5, List.of(SEASONAL)),
                Arguments.of("bem avaliado", new Profile(), candidate(SNOW, "Chile", 6, false, 4.0), 1.0,
                        List.of(HIGHLY_RATED)),
                Arguments.of("média abaixo de 4", new Profile(), candidate(SNOW, "Chile", 6, false, 3.9), 0.0,
                        List.of()),
                Arguments.of("promoção", new Profile(), candidate(SNOW, "Chile", 6, true, null), 0.5,
                        List.of(ON_PROMOTION)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleSignals")
    void eachSignalAddsItsWeightAndReason(String name, Profile profile, Candidate candidate, double score,
            List<RecommendationReason> reasons) {
        Scored scored = RecommendationRules.score(profile, candidate);

        assertThat(scored.score()).isEqualTo(score);
        assertThat(scored.reasons()).containsExactlyElementsOf(reasons);
    }

    @Test
    void signalsAddUpAcrossSources() {
        Profile profile = new Profile()
                .addTrip(UUID.randomUUID(), BEACH, "Brasil", Month.MARCH)
                .addFavorite(UUID.randomUUID(), BEACH, "Brasil")
                .addRating(UUID.randomUUID(), BEACH, "Brasil", 5);

        Scored scored = RecommendationRules.score(profile, candidate(BEACH, "Brasil", 4, true, 4.5));

        // categoria 3 + 2 + 2, país 2, época 1,5, avaliação 1, promoção 0,5
        assertThat(scored.score()).isEqualTo(12.0);
        assertThat(scored.reasons()).containsExactly(SIMILAR_CATEGORY, SIMILAR_DESTINATION, SEASONAL, HIGHLY_RATED,
                ON_PROMOTION);
    }

    @ParameterizedTest(name = "{0} perto de {1}: {2}")
    @CsvSource({
            "MARCH, MARCH, true",
            "APRIL, MARCH, true",
            "FEBRUARY, MARCH, true",
            "MAY, MARCH, false",
            "JANUARY, DECEMBER, true",
            "DECEMBER, JANUARY, true",
            "NOVEMBER, JANUARY, false" })
    void seasonIsOneMonthAroundTheTripsIncludingYearTurn(Month month, Month traveled, boolean expected) {
        assertThat(RecommendationRules.isInSeason(month, Set.of(traveled))).isEqualTo(expected);
    }

    @Test
    void neverSuggestsPackagesTheClientAlreadyBookedFavoritedOrRated() {
        Candidate booked = candidate(BEACH, "Brasil", 3, true, 5.0);
        Candidate favorite = candidate(BEACH, "Brasil", 3, true, 5.0);
        Candidate rated = candidate(BEACH, "Brasil", 3, true, 5.0);
        Candidate traveled = candidate(BEACH, "Brasil", 3, true, 5.0);
        Candidate fresh = candidate(SNOW, "Chile", 8, false, null);
        Profile profile = new Profile()
                .addBooked(booked.travelPackageId())
                .addFavorite(favorite.travelPackageId(), BEACH, "Brasil")
                .addRating(rated.travelPackageId(), BEACH, "Brasil", 5)
                .addTrip(traveled.travelPackageId(), BEACH, "Brasil", Month.MARCH);

        assertThat(RecommendationRules.rank(profile, List.of(booked, favorite, rated, traveled, fresh), 10))
                .extracting(Scored::travelPackageId)
                .containsExactly(fresh.travelPackageId());
    }

    @Test
    void ordersByScoreThenNearestDepartureThenIdAndRespectsLimit() {
        Profile profile = new Profile().addTrip(UUID.randomUUID(), BEACH, "Brasil", null);
        Candidate beachLater = candidate(BEACH, "Peru", 9, false, null);
        Candidate beachSooner = candidate(BEACH, "Peru", 2, false, null);
        Candidate other = candidate(SNOW, "Chile", 1, false, null);

        List<Scored> ranked = RecommendationRules.rank(profile, List.of(other, beachLater, beachSooner), 2);

        assertThat(ranked).extracting(Scored::travelPackageId)
                .containsExactly(beachSooner.travelPackageId(), beachLater.travelPackageId());
    }

    @Test
    void coldStartRanksByRatingPromotionAndDate() {
        Candidate plain = candidate(SNOW, "Chile", 1, false, null);
        Candidate onSale = candidate(SNOW, "Chile", 5, true, null);
        Candidate loved = candidate(SNOW, "Chile", 9, false, 4.8);

        assertThat(RecommendationRules.rank(new Profile(), List.of(plain, onSale, loved), 10))
                .extracting(Scored::travelPackageId)
                .containsExactly(loved.travelPackageId(), onSale.travelPackageId(), plain.travelPackageId());
    }

    @Test
    void isDeterministic() {
        Profile profile = new Profile().addFavorite(UUID.randomUUID(), CITY, "Argentina");
        List<Candidate> candidates = List.of(candidate(CITY, "Argentina", 4, false, null),
                candidate(CITY, "Argentina", 4, false, null), candidate(BEACH, "Brasil", 4, false, null));

        assertThat(RecommendationRules.rank(profile, candidates, 10))
                .isEqualTo(RecommendationRules.rank(profile, candidates.reversed(), 10));
    }
}
