package br.com.familhas.city;

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

import br.com.familhas.city.dto.CityFilter;
import br.com.familhas.city.dto.CityRequest;
import br.com.familhas.city.dto.CityResponse;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;

@Service
@Transactional
public class CityService {

    static final Set<String> SORTABLE = Set.of("name", "country", "createdAt", "updatedAt");

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CityResponse> findAll(CityFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(cityRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    private static Specification<CityEntity> toSpecification(CityFilter filter) {
        List<Specification<CityEntity>> specs = new ArrayList<>();
        if (filter.cityType() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("cityType"), filter.cityType()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Specs.hasText(filter.q())) {
            specs.add(Specs.containsIgnoreCase("name", filter.q()));
        }
        return Specification.allOf(specs);
    }

    @Transactional(readOnly = true)
    public CityResponse findById(UUID id) {
        return toResponse(getCityOrThrow(id));
    }

    public CityResponse create(CityRequest request) {
        CityEntity city = new CityEntity();
        apply(city, request);
        return toResponse(cityRepository.save(city));
    }

    public CityResponse update(UUID id, CityRequest request) {
        CityEntity city = getCityOrThrow(id);
        apply(city, request);
        return toResponse(cityRepository.save(city));
    }

    public void delete(UUID id) {
        CityEntity city = getCityOrThrow(id);
        cityRepository.delete(city);
        cityRepository.flush();
    }

    private CityEntity getCityOrThrow(UUID id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + id));
    }

    private void apply(CityEntity city, CityRequest request) {
        city.setName(request.name().trim());
        city.setCountry(request.country().trim());
        city.setCityType(request.cityType());
        city.setStatus(request.status());
    }

    private CityResponse toResponse(CityEntity city) {
        return new CityResponse(
                city.getId(),
                city.getName(),
                city.getCountry(),
                city.getCityType(),
                city.getStatus());
    }
}



