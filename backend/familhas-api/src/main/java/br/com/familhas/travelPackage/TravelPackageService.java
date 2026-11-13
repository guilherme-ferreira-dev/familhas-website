package br.com.familhas.travelPackage;

import java.math.BigDecimal;
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

import br.com.familhas.carRental.CarRentalOfferEntity;
import br.com.familhas.category.CategoryEntity;
import br.com.familhas.city.CityEntity;
import br.com.familhas.common.Currencies;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.hotel.RoomOfferEntity;
import br.com.familhas.travelPackage.dto.TravelPackageComponentRequest;
import br.com.familhas.travelPackage.dto.TravelPackageComponentResponse;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageRequest;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

@Service
@Transactional
public class TravelPackageService {

    static final Set<String> SORTABLE = Set.of("title", "price", "createdAt", "updatedAt");

    private final TravelPackageRepository travelPackageRepository;
    private final EntityManager entityManager;

    public TravelPackageService(TravelPackageRepository travelPackageRepository, EntityManager entityManager) {
        this.travelPackageRepository = travelPackageRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<TravelPackageResponse> findAll(TravelPackageFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        validatePriceRange(filter);
        return PageResponse.of(travelPackageRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public TravelPackageResponse findById(UUID id) {
        return toResponse(getTravelPackageOrThrow(id));
    }

    public TravelPackageResponse create(TravelPackageRequest request) {
        validateTravelPackageBusinessRules(request);
        TravelPackageEntity travelPackage = new TravelPackageEntity();
        apply(travelPackage, request);
        return toResponse(travelPackageRepository.save(travelPackage));
    }

    public TravelPackageResponse update(UUID id, TravelPackageRequest request) {
        validateTravelPackageBusinessRules(request);
        TravelPackageEntity travelPackage = getTravelPackageOrThrow(id);
        apply(travelPackage, request);
        return toResponse(travelPackageRepository.save(travelPackage));
    }

    /**
     * Componentes são removidos em cascata. Avaliações, favoritos e viagens de clientes bloqueiam
     * a exclusão (FK): o flush antecipa a violação para virar 409 no GlobalExceptionHandler.
     */
    public void delete(UUID id) {
        TravelPackageEntity travelPackage = getTravelPackageOrThrow(id);
        travelPackageRepository.delete(travelPackage);
        travelPackageRepository.flush();
    }

    private void validateTravelPackageBusinessRules(TravelPackageRequest request) {
        if (request.originCityId().equals(request.destinationCityId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "originCityId and destinationCityId must be different");
        }
        BigDecimal promotion = request.pricePromotion();
        if (promotion != null) {
            if (promotion.compareTo(request.price()) >= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "pricePromotion must be less than price when provided");
            }
        }
    }

    private static void validatePriceRange(TravelPackageFilter filter) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minPrice must be less than or equal to maxPrice");
        }
    }

    private static Specification<TravelPackageEntity> toSpecification(TravelPackageFilter filter) {
        List<Specification<TravelPackageEntity>> specs = new ArrayList<>();
        if (filter.originCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("origin").get("id"), filter.originCityId()));
        }
        if (filter.destinationCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("destination").get("id"), filter.destinationCityId()));
        }
        if (filter.categoryId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("id"), filter.categoryId()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.minPrice() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(effectivePrice(root, cb), filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(effectivePrice(root, cb), filter.maxPrice()));
        }
        if (Specs.hasText(filter.q())) {
            specs.add(Specs.containsIgnoreCase("title", filter.q()));
        }
        if (Boolean.TRUE.equals(filter.onPromotion())) {
            specs.add((root, query, cb) -> cb.and(cb.isNotNull(root.get("pricePromotion")),
                    cb.lessThan(root.<BigDecimal>get("pricePromotion"), root.<BigDecimal>get("price"))));
        }
        return Specification.allOf(specs);
    }

    /** Preço que o cliente paga: promoção quando existir, senão o preço cheio. */
    private static Expression<BigDecimal> effectivePrice(Root<TravelPackageEntity> root, CriteriaBuilder cb) {
        return cb.coalesce(root.<BigDecimal>get("pricePromotion"), root.<BigDecimal>get("price"));
    }

    private TravelPackageEntity getTravelPackageOrThrow(UUID id) {
        return travelPackageRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Travel package not found: " + id));
    }

    private <T> T getReferenceOrThrow(Class<T> entityClass, UUID id, String label) {
        T entity = entityManager.find(entityClass, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found: " + id);
        }
        return entity;
    }

    private void apply(TravelPackageEntity travelPackage, TravelPackageRequest request) {
        travelPackage.setTitle(request.title().trim());
        travelPackage.setPrice(request.price());
        travelPackage.setPricePromotion(request.pricePromotion());
        travelPackage.setCurrency(Currencies.resolve(request.currency(), travelPackage.getCurrency()));
        travelPackage.setStatus(request.status());
        travelPackage.setImageUrl(blankToNull(request.imageUrl()));
        travelPackage.setFlight(getReferenceOrThrow(FlightEntity.class, request.flightId(), "Flight"));
        travelPackage.setCategory(getReferenceOrThrow(CategoryEntity.class, request.categoryId(), "Category"));
        travelPackage.setOrigin(getReferenceOrThrow(CityEntity.class, request.originCityId(), "Origin city"));
        travelPackage.setDestination(
                getReferenceOrThrow(CityEntity.class, request.destinationCityId(), "Destination city"));
        if (request.components() != null) {
            // Mesma instância de lista: exigência do orphanRemoval do Hibernate.
            List<TravelPackageComponentEntity> components = travelPackage.getComponents();
            components.clear();
            for (TravelPackageComponentRequest componentRequest : request.components()) {
                components.add(toComponent(travelPackage, componentRequest));
            }
        }
    }

    private TravelPackageComponentEntity toComponent(TravelPackageEntity travelPackage,
            TravelPackageComponentRequest request) {
        TravelPackageComponentEntity component = new TravelPackageComponentEntity();
        component.setTravelPackage(travelPackage);
        component.setComponentType(request.type());
        component.setQuantity(request.quantity());
        if (request.type() == PackageComponentType.HOTEL_STAY) {
            if (request.roomOfferId() == null || request.carRentalOfferId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "HOTEL_STAY component requires roomOfferId and no carRentalOfferId");
            }
            component.setRoomOffer(getReferenceOrThrow(RoomOfferEntity.class, request.roomOfferId(), "Room offer"));
        } else {
            if (request.carRentalOfferId() == null || request.roomOfferId() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "CAR_RENTAL component requires carRentalOfferId and no roomOfferId");
            }
            component.setCarRentalOffer(
                    getReferenceOrThrow(CarRentalOfferEntity.class, request.carRentalOfferId(), "Car rental offer"));
        }
        return component;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private TravelPackageResponse toResponse(TravelPackageEntity travelPackage) {
        return new TravelPackageResponse(
                travelPackage.getId(),
                travelPackage.getTitle(),
                travelPackage.getPrice(),
                travelPackage.getPricePromotion(),
                travelPackage.getCurrency(),
                travelPackage.getStatus(),
                travelPackage.getImageUrl(),
                travelPackage.getFlight().getId(),
                travelPackage.getCategory().getId(),
                travelPackage.getOrigin().getId(),
                travelPackage.getDestination().getId(),
                travelPackage.getComponents().stream().map(TravelPackageService::toComponentResponse).toList());
    }

    private static TravelPackageComponentResponse toComponentResponse(TravelPackageComponentEntity component) {
        return new TravelPackageComponentResponse(
                component.getId(),
                component.getComponentType(),
                component.getRoomOffer() != null ? component.getRoomOffer().getId() : null,
                component.getCarRentalOffer() != null ? component.getCarRentalOffer().getId() : null,
                component.getQuantity());
    }
}
