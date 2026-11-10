package br.com.familhas.rating;

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

import br.com.familhas.client.ClientEntity;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.rating.dto.ClientRatingRequest;
import br.com.familhas.rating.dto.RatingFilter;
import br.com.familhas.rating.dto.RatingRequest;
import br.com.familhas.rating.dto.RatingResponse;
import br.com.familhas.travelPackage.TravelPackageEntity;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class RatingService {

    static final Set<String> SORTABLE = Set.of("rating", "createdAt", "updatedAt");

    private final RatingRepository ratingRepository;
    private final EntityManager entityManager;

    public RatingService(RatingRepository ratingRepository, EntityManager entityManager) {
        this.ratingRepository = ratingRepository;
        this.entityManager = entityManager;
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
        assertUniqueRatingPerClientAndPackage(request.clientId(), request.travelPackageId(), null);
        RatingEntity rating = new RatingEntity();
        apply(rating, request);
        return toResponse(ratingRepository.save(rating));
    }

    public RatingResponse update(UUID id, RatingRequest request) {
        assertUniqueRatingPerClientAndPackage(request.clientId(), request.travelPackageId(), id);
        RatingEntity rating = getRatingOrThrow(id);
        apply(rating, request);
        return toResponse(ratingRepository.save(rating));
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Client already rated this travel package");
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

    /** Avaliações do próprio cliente autenticado (/api/me/ratings). */
    @Transactional(readOnly = true)
    public PageResponse<RatingResponse> findAllForClient(UUID clientId, Pageable pageable) {
        return findAll(new RatingFilter(clientId, null), pageable);
    }

    public RatingResponse createForClient(UUID clientId, ClientRatingRequest request) {
        return create(new RatingRequest(request.rating(), clientId, request.travelPackageId()));
    }

    /** 404 (e não 403) quando a avaliação é de outro cliente: não revela existência. */
    public RatingResponse updateForClient(UUID clientId, UUID ratingId, ClientRatingRequest request) {
        assertOwnedByClient(ratingId, clientId);
        return update(ratingId, new RatingRequest(request.rating(), clientId, request.travelPackageId()));
    }

    public void deleteForClient(UUID clientId, UUID ratingId) {
        assertOwnedByClient(ratingId, clientId);
        delete(ratingId);
    }

    private void assertOwnedByClient(UUID ratingId, UUID clientId) {
        RatingEntity rating = getRatingOrThrow(ratingId);
        if (!rating.getClient().getId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rating not found: " + ratingId);
        }
    }

    private RatingResponse toResponse(RatingEntity rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getRating(),
                rating.getClient().getId(),
                rating.getTravelPackage().getId());
    }
}



