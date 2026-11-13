package br.com.familhas.rating;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.BookingRepository;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.rating.dto.ClientRatingRequest;
import br.com.familhas.rating.dto.MyRatingResponse;
import br.com.familhas.rating.dto.RatingFilter;
import br.com.familhas.rating.dto.RatingRequest;
import br.com.familhas.rating.dto.RatingResponse;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class RatingService {

    static final Set<String> SORTABLE = Set.of("rating", "createdAt", "updatedAt");

    private final RatingRepository ratingRepository;
    private final BookingRepository bookingRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public RatingService(RatingRepository ratingRepository, BookingRepository bookingRepository,
            EntityManager entityManager, Clock clock) {
        this.ratingRepository = ratingRepository;
        this.bookingRepository = bookingRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> findAll(RatingFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(ratingRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    private static Specification<RatingEntity> toSpecification(RatingFilter filter) {
        List<Specification<RatingEntity>> specs = new ArrayList<>();
        if (filter.clientId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("client").get("id"), filter.clientId()));
        }
        if (filter.travelPackageId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("travelPackage").get("id"), filter.travelPackageId()));
        }
        return Specification.allOf(specs);
    }

    @Transactional(readOnly = true)
    public RatingResponse findById(UUID id) {
        return toResponse(getRatingOrThrow(id));
    }

    public RatingResponse create(RatingRequest request) {
        return toResponse(createEntity(request));
    }

    public RatingResponse update(UUID id, RatingRequest request) {
        return toResponse(updateEntity(getRatingOrThrow(id), request));
    }

    private RatingEntity createEntity(RatingRequest request) {
        assertUniqueRatingPerClientAndPackage(request.clientId(), request.travelPackageId(), null);
        RatingEntity rating = new RatingEntity();
        apply(rating, request);
        return ratingRepository.save(rating);
    }

    private RatingEntity updateEntity(RatingEntity rating, RatingRequest request) {
        assertUniqueRatingPerClientAndPackage(request.clientId(), request.travelPackageId(), rating.getId());
        apply(rating, request);
        return ratingRepository.save(rating);
    }

    public void delete(UUID id) {
        RatingEntity rating = getRatingOrThrow(id);
        ratingRepository.delete(rating);
    }

    private void assertUniqueRatingPerClientAndPackage(UUID clientId, UUID travelPackageId, UUID excludeRatingId) {
        boolean duplicate;
        if (excludeRatingId == null) {
            duplicate = ratingRepository.existsByClient_IdAndTravelPackage_Id(clientId, travelPackageId);
        } else {
            duplicate = ratingRepository.existsByClient_IdAndTravelPackage_IdAndIdNot(
                    clientId, travelPackageId, excludeRatingId);
        }
        if (duplicate) {
            throw new BusinessException(HttpStatus.CONFLICT, "RATING_ALREADY_EXISTS",
                    "O cliente ja avaliou este pacote.");
        }
    }

    private RatingEntity getRatingOrThrow(UUID id) {
        return ratingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating not found: " + id));
    }

    private <T> T getReferenceOrThrow(Class<T> entityClass, UUID id, String label) {
        T entity = entityManager.find(entityClass, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found: " + id);
        }
        return entity;
    }

    private void apply(RatingEntity rating, RatingRequest request) {
        rating.setRating(request.rating());
        rating.setClient(getReferenceOrThrow(ClientEntity.class, request.clientId(), "Client"));
        rating.setTravelPackage(
                getReferenceOrThrow(TravelPackageEntity.class, request.travelPackageId(), "Travel package"));
    }

    /** Avaliações do próprio cliente autenticado (/api/me/ratings), com o pacote resumido. */
    @Transactional(readOnly = true)
    public PageResponse<MyRatingResponse> findAllForClient(UUID clientId, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(ratingRepository.findByClient_Id(clientId, pageable), RatingService::toMyResponse);
    }

    /** Só pacote de viagem passada do próprio cliente (ADR 0010, D5). */
    public MyRatingResponse createForClient(UUID clientId, ClientRatingRequest request) {
        assertTraveledWith(clientId, request.travelPackageId());
        RatingEntity created = createEntity(new RatingRequest(request.rating(), clientId, request.travelPackageId()));
        ratingRepository.flush();
        return toMyResponse(created);
    }

    /** 404 (e não 403) quando a avaliação é de outro cliente: não revela existência. */
    public MyRatingResponse updateForClient(UUID clientId, UUID ratingId, ClientRatingRequest request) {
        RatingEntity rating = getOwnedOrThrow(ratingId, clientId);
        if (!rating.getTravelPackage().getId().equals(request.travelPackageId())) {
            assertTraveledWith(clientId, request.travelPackageId());
        }
        updateEntity(rating, new RatingRequest(request.rating(), clientId, request.travelPackageId()));
        ratingRepository.flush();
        return toMyResponse(rating);
    }

    public void deleteForClient(UUID clientId, UUID ratingId) {
        ratingRepository.delete(getOwnedOrThrow(ratingId, clientId));
    }

    private void assertTraveledWith(UUID clientId, UUID travelPackageId) {
        if (!bookingRepository.existsPastTrip(clientId, travelPackageId, BusinessTime.today(clock))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "RATING_NOT_ALLOWED",
                    "So e possivel avaliar pacotes de viagens ja realizadas.");
        }
    }

    private RatingEntity getOwnedOrThrow(UUID ratingId, UUID clientId) {
        return ratingRepository.findById(ratingId)
                .filter(rating -> rating.getClient().getId().equals(clientId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "RATING_NOT_FOUND",
                        "Avaliacao nao encontrada: " + ratingId));
    }

    private static MyRatingResponse toMyResponse(RatingEntity rating) {
        return new MyRatingResponse(rating.getId(), rating.getRating(),
                PackageSummaryResponse.of(rating.getTravelPackage()), rating.getCreatedAt(), rating.getUpdatedAt());
    }

    private RatingResponse toResponse(RatingEntity rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getRating(),
                rating.getClient().getId(),
                rating.getTravelPackage().getId());
    }
}



