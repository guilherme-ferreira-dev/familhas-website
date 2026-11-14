package br.com.familhas.recommendation;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras da recomendação v1 (ADR 0010, D8): função pura e determinística, sem banco nem relógio.
 * Os pesos ficam aqui, documentados no ADR; mudar um peso é mudança de código com teste.
 */
public final class RecommendationRules {

    static final int TRIP_CATEGORY_WEIGHT = 3;
    static final int FAVORITE_CATEGORY_WEIGHT = 2;
    static final int GOOD_RATING_CATEGORY_WEIGHT = 2;
    static final int BAD_RATING_CATEGORY_WEIGHT = -3;
    static final int GOOD_RATING = 4;
    static final int BAD_RATING = 2;

    static final double SIMILAR_DESTINATION_SCORE = 2.0;
    static final double SEASONAL_SCORE = 1.5;
    static final double HIGHLY_RATED_SCORE = 1.0;
    static final double HIGHLY_RATED_AVERAGE = 4.0;
    static final double ON_PROMOTION_SCORE = 0.5;

    private RecommendationRules() {
    }

    /** Pacote candidato, já filtrado (ativo, voo ativo, embarque futuro). averageRating nulo: sem avaliações. */
    public record Candidate(UUID travelPackageId, UUID categoryId, String destinationCountry,
            LocalDateTime departureAt, boolean onPromotion, Double averageRating) {
    }

    public record Scored(UUID travelPackageId, double score, List<RecommendationReason> reasons) {
    }

    /** Sinais do cliente. Monte com os métodos add*; pacotes vistos (reservados, favoritos, avaliados) saem. */
    public static final class Profile {

        private final Map<UUID, Integer> categoryWeights = new HashMap<>();
        private final Set<String> countries = new HashSet<>();
        private final Set<Month> travelMonths = new HashSet<>();
        private final Set<UUID> seenPackageIds = new HashSet<>();

        /** Viagem confirmada ou concluída. Sem pacote (reserva avulsa), só a época conta. */
        public Profile addTrip(UUID travelPackageId, UUID categoryId, String country, Month month) {
            if (travelPackageId != null) {
                seenPackageIds.add(travelPackageId);
                addCategory(categoryId, TRIP_CATEGORY_WEIGHT);
                addCountry(country);
            }
            if (month != null) {
                travelMonths.add(month);
            }
            return this;
        }

        /** Reserva em andamento ou paga: o pacote não é sugerido de novo. */
        public Profile addBooked(UUID travelPackageId) {
            seenPackageIds.add(travelPackageId);
            return this;
        }

        public Profile addFavorite(UUID travelPackageId, UUID categoryId, String country) {
            seenPackageIds.add(travelPackageId);
            addCategory(categoryId, FAVORITE_CATEGORY_WEIGHT);
            addCountry(country);
            return this;
        }

        public Profile addRating(UUID travelPackageId, UUID categoryId, String country, int rating) {
            seenPackageIds.add(travelPackageId);
            if (rating >= GOOD_RATING) {
                addCategory(categoryId, GOOD_RATING_CATEGORY_WEIGHT);
                addCountry(country);
            } else if (rating <= BAD_RATING) {
                addCategory(categoryId, BAD_RATING_CATEGORY_WEIGHT);
            }
            return this;
        }

        private void addCategory(UUID categoryId, int weight) {
            if (categoryId != null) {
                categoryWeights.merge(categoryId, weight, Integer::sum);
            }
        }

        private void addCountry(String country) {
            if (country != null && !country.isBlank()) {
                countries.add(normalize(country));
            }
        }
    }

    /** Pontua, descarta o que o cliente já viu e ordena: pontuação, embarque mais próximo, id. */
    public static List<Scored> rank(Profile profile, List<Candidate> candidates, int limit) {
        return candidates.stream()
                .filter(candidate -> !profile.seenPackageIds.contains(candidate.travelPackageId()))
                .map(candidate -> new Ranked(candidate, score(profile, candidate)))
                .sorted(Comparator.comparingDouble((Ranked r) -> r.scored().score()).reversed()
                        .thenComparing(r -> r.candidate().departureAt(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(r -> r.candidate().travelPackageId()))
                .limit(limit)
                .map(Ranked::scored)
                .toList();
    }

    static Scored score(Profile profile, Candidate candidate) {
        double score = 0;
        List<RecommendationReason> reasons = new ArrayList<>();

        int categoryWeight = profile.categoryWeights.getOrDefault(candidate.categoryId(), 0);
        if (categoryWeight > 0) {
            score += categoryWeight;
            reasons.add(RecommendationReason.SIMILAR_CATEGORY);
        } else if (categoryWeight < 0) {
            score += categoryWeight;
        }
        if (candidate.destinationCountry() != null
                && profile.countries.contains(normalize(candidate.destinationCountry()))) {
            score += SIMILAR_DESTINATION_SCORE;
            reasons.add(RecommendationReason.SIMILAR_DESTINATION);
        }
        if (candidate.departureAt() != null && isInSeason(candidate.departureAt().getMonth(), profile.travelMonths)) {
            score += SEASONAL_SCORE;
            reasons.add(RecommendationReason.SEASONAL);
        }
        if (candidate.averageRating() != null && candidate.averageRating() >= HIGHLY_RATED_AVERAGE) {
            score += HIGHLY_RATED_SCORE;
            reasons.add(RecommendationReason.HIGHLY_RATED);
        }
        if (candidate.onPromotion()) {
            score += ON_PROMOTION_SCORE;
            reasons.add(RecommendationReason.ON_PROMOTION);
        }
        return new Scored(candidate.travelPackageId(), score, List.copyOf(reasons));
    }

    /** Até um mês de distância, contando a virada do ano (dezembro e janeiro são vizinhos). */
    static boolean isInSeason(Month month, Set<Month> travelMonths) {
        for (Month traveled : travelMonths) {
            int distance = Math.abs(month.getValue() - traveled.getValue());
            if (Math.min(distance, 12 - distance) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String country) {
        return Normalizer.normalize(country.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private record Ranked(Candidate candidate, Scored scored) {
    }
}
