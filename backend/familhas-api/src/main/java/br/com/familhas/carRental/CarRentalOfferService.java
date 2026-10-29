package br.com.familhas.carRental;

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

import br.com.familhas.carRental.dto.CarRentalOfferFilter;
import br.com.familhas.carRental.dto.CarRentalOfferRequest;
import br.com.familhas.carRental.dto.CarRentalOfferResponse;
import br.com.familhas.city.CityEntity;
import br.com.familhas.common.Currencies;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class CarRentalOfferService {

    static final Set<String> SORTABLE = Set.of("pricePerDay", "seats", "companyName", "createdAt", "updatedAt");

    private final CarRentalOfferRepository carRentalOfferRepository;
    private final EntityManager entityManager;

    public CarRentalOfferService(CarRentalOfferRepository carRentalOfferRepository, EntityManager entityManager) {
        this.carRentalOfferRepository = carRentalOfferRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<CarRentalOfferResponse> findAll(CarRentalOfferFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(carRentalOfferRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public CarRentalOfferResponse findById(UUID id) {
        return toResponse(getOfferOrThrow(id));
    }

    public CarRentalOfferResponse create(CarRentalOfferRequest request) {
        CarRentalOfferEntity offer = new CarRentalOfferEntity();
        apply(offer, request);
        return toResponse(carRentalOfferRepository.save(offer));
    }

    public CarRentalOfferResponse update(UUID id, CarRentalOfferRequest request) {
        CarRentalOfferEntity offer = getOfferOrThrow(id);
        apply(offer, request);
        return toResponse(carRentalOfferRepository.save(offer));
    }

    /** Oferta usada em pacote não é excluída: a FK vira 409. */
    public void delete(UUID id) {
        carRentalOfferRepository.delete(getOfferOrThrow(id));
        carRentalOfferRepository.flush();
    }

    private static Specification<CarRentalOfferEntity> toSpecification(CarRentalOfferFilter filter) {
        List<Specification<CarRentalOfferEntity>> specs = new ArrayList<>();
        if (filter.cityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("city").get("id"), filter.cityId()));
        }
        if (filter.carCategory() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("carCategory"), filter.carCategory()));
        }
        if (filter.transmission() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("transmission"), filter.transmission()));
        }
        if (filter.minSeats() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("seats"), filter.minSeats()));
        }
        if (filter.maxPricePerDay() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(
                    root.<BigDecimal>get("pricePerDay"), filter.maxPricePerDay()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        return Specification.allOf(specs);
    }

    private CarRentalOfferEntity getOfferOrThrow(UUID id) {
        return carRentalOfferRepository.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car rental offer not found: " + id));
    }

    private void apply(CarRentalOfferEntity offer, CarRentalOfferRequest request) {
        CityEntity city = entityManager.find(CityEntity.class, request.cityId());
        if (city == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + request.cityId());
        }
        offer.setCity(city);
        offer.setCompanyName(request.companyName().trim());
        offer.setCarModel(request.carModel().trim());
        offer.setCarCategory(request.carCategory());
        offer.setTransmission(request.transmission());
        offer.setSeats(request.seats());
        offer.setPricePerDay(request.pricePerDay());
        offer.setCurrency(Currencies.resolve(request.currency(), offer.getCurrency()));
        offer.setStatus(request.status());
    }

    private CarRentalOfferResponse toResponse(CarRentalOfferEntity offer) {
        return new CarRentalOfferResponse(
                offer.getId(),
                offer.getCity().getId(),
                offer.getCompanyName(),
                offer.getCarModel(),
                offer.getCarCategory(),
                offer.getTransmission(),
                offer.getSeats(),
                offer.getPricePerDay(),
                offer.getCurrency(),
                offer.getStatus());
    }
}
