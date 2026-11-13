package br.com.familhas.recommendation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingItemEntity;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.rating.RatingRepository;
import br.com.familhas.rating.RatingRepository.PackageRatingStats;
import br.com.familhas.recommendation.RecommendationRules.Candidate;
import br.com.familhas.recommendation.RecommendationRules.Profile;
import br.com.familhas.recommendation.dto.RecommendationResponse;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;
import br.com.familhas.trip.TripService;

/**
 * Recomendação v1 por regras (ADR 0010, D8). Monta o perfil do cliente (viagens, reservas, favoritos e
 * avaliações), carrega os candidatos do catálogo curado e delega a pontuação a {@link RecommendationRules}.
 */
@Service
@Transactional(readOnly = true)
public class RuleBasedRecommendationService implements RecommendationService {

    private final BookingRepository bookingRepository;
    private final ClientRepository clientRepository;
    private final RatingRepository ratingRepository;
    private final TravelPackageRepository travelPackageRepository;
    private final Clock clock;

    public RuleBasedRecommendationService(BookingRepository bookingRepository, ClientRepository clientRepository,
            RatingRepository ratingRepository, TravelPackageRepository travelPackageRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clientRepository = clientRepository;
        this.ratingRepository = ratingRepository;
        this.travelPackageRepository = travelPackageRepository;
        this.clock = clock;
    }

    @Override
    public List<RecommendationResponse> recommend(UUID clientId, int limit) {
        int bounded = Math.clamp(limit, 1, MAX_LIMIT);
        Map<UUID, TravelPackageEntity> candidates = candidates(BusinessTime.today(clock)).stream()
                .collect(Collectors.toMap(TravelPackageEntity::getId, Function.identity()));
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, Double> averages = ratingRepository.findPackageStats().stream()
                .collect(Collectors.toMap(PackageRatingStats::getTravelPackageId, PackageRatingStats::getAverage));

        List<Candidate> scored = candidates.values().stream()
                .map(p -> new Candidate(p.getId(), p.getCategory().getId(), p.getDestination().getCountry(),
                        p.getFlight().getBoardingDate(), PackageSummaryResponse.onPromotion(p),
                        averages.get(p.getId())))
                .toList();
        return RecommendationRules.rank(profile(clientId), scored, bounded).stream()
                .map(s -> new RecommendationResponse(PackageSummaryResponse.of(candidates.get(s.travelPackageId())),
                        s.score(), s.reasons()))
                .toList();
    }

    private Profile profile(UUID clientId) {
        Profile profile = new Profile();
        for (BookingEntity trip : bookingRepository.findByClientIdAndStatusIn(clientId, TripService.TRIP_STATUSES)) {
            TravelPackageEntity travelPackage = trip.getTravelPackage();
            var month = trip.getItems().stream().map(BookingItemEntity::getStartDate)
                    .min(Comparator.naturalOrder()).map(LocalDate::getMonth).orElse(null);
            profile.addTrip(travelPackage == null ? null : travelPackage.getId(),
                    travelPackage == null ? null : travelPackage.getCategory().getId(),
                    travelPackage == null ? null : travelPackage.getDestination().getCountry(), month);
        }
        bookingRepository.findBookedPackageIds(clientId).forEach(profile::addBooked);
        clientRepository.findById(clientId).ifPresent(client -> client.getFavorites().forEach(p ->
                profile.addFavorite(p.getId(), p.getCategory().getId(), p.getDestination().getCountry())));
        ratingRepository.findByClient_Id(clientId).forEach(r -> profile.addRating(r.getTravelPackage().getId(),
                r.getTravelPackage().getCategory().getId(), r.getTravelPackage().getDestination().getCountry(),
                r.getRating()));
        return profile;
    }

    /** Pacotes ativos com voo ativo e embarque a partir de amanhã (catálogo curado, dezenas de itens). */
    private List<TravelPackageEntity> candidates(LocalDate today) {
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        Specification<TravelPackageEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EntityStatus.ACTIVE),
                cb.equal(root.get("flight").get("status"), EntityStatus.ACTIVE),
                cb.greaterThanOrEqualTo(root.get("flight").<LocalDateTime>get("boardingDate"), tomorrow));
        return travelPackageRepository.findAll(spec);
    }
}
