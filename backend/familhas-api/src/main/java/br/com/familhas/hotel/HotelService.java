package br.com.familhas.hotel;

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

import br.com.familhas.city.CityEntity;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.hotel.dto.HotelFilter;
import br.com.familhas.hotel.dto.HotelRequest;
import br.com.familhas.hotel.dto.HotelResponse;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class HotelService {

    static final Set<String> SORTABLE = Set.of("name", "stars", "createdAt", "updatedAt");

    private final HotelRepository hotelRepository;
    private final EntityManager entityManager;

    public HotelService(HotelRepository hotelRepository, EntityManager entityManager) {
        this.hotelRepository = hotelRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelResponse> findAll(HotelFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(hotelRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public HotelResponse findById(UUID id) {
        return toResponse(getHotelOrThrow(id));
    }

    public HotelResponse create(HotelRequest request) {
        HotelEntity hotel = new HotelEntity();
        apply(hotel, request);
        return toResponse(hotelRepository.save(hotel));
    }

    public HotelResponse update(UUID id, HotelRequest request) {
        HotelEntity hotel = getHotelOrThrow(id);
        apply(hotel, request);
        return toResponse(hotelRepository.save(hotel));
    }

    /** Hotel com ofertas de quarto não é excluído: a FK vira 409. Inative pelo status. */
    public void delete(UUID id) {
        hotelRepository.delete(getHotelOrThrow(id));
        hotelRepository.flush();
    }

    private static Specification<HotelEntity> toSpecification(HotelFilter filter) {
        List<Specification<HotelEntity>> specs = new ArrayList<>();
        if (filter.cityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("city").get("id"), filter.cityId()));
        }
        if (filter.minStars() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("stars"), filter.minStars()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Specs.hasText(filter.q())) {
            specs.add(Specs.containsIgnoreCase("name", filter.q()));
        }
        return Specification.allOf(specs);
    }

    private HotelEntity getHotelOrThrow(UUID id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + id));
    }

    private void apply(HotelEntity hotel, HotelRequest request) {
        CityEntity city = entityManager.find(CityEntity.class, request.cityId());
        if (city == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + request.cityId());
        }
        hotel.setName(request.name().trim());
        hotel.setCity(city);
        hotel.setAddress(blankToNull(request.address()));
        hotel.setStars(request.stars());
        hotel.setDescription(blankToNull(request.description()));
        hotel.setImageUrl(blankToNull(request.imageUrl()));
        hotel.setStatus(request.status());
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private HotelResponse toResponse(HotelEntity hotel) {
        return new HotelResponse(
                hotel.getId(),
                hotel.getName(),
                hotel.getCity().getId(),
                hotel.getAddress(),
                hotel.getStars(),
                hotel.getDescription(),
                hotel.getImageUrl(),
                hotel.getStatus());
    }
}
