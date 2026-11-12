package br.com.familhas.flight;

import java.time.LocalDateTime;
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
import br.com.familhas.common.Currencies;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.flight.dto.FlightFilter;
import br.com.familhas.flight.dto.FlightRequest;
import br.com.familhas.flight.dto.FlightResponse;
import jakarta.persistence.EntityManager;

@Service
@Transactional
public class FlightService {

    static final Set<String> SORTABLE = Set.of("boardingDate", "returnDate", "companyName", "pricePerPassenger",
            "createdAt", "updatedAt");

    private final FlightRepository flightRepository;
    private final EntityManager entityManager;

    public FlightService(FlightRepository flightRepository, EntityManager entityManager) {
        this.flightRepository = flightRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<FlightResponse> findAll(FlightFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        if (filter.departureFrom() != null && filter.departureTo() != null
                && filter.departureFrom().isAfter(filter.departureTo())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "departureFrom must be on or before departureTo");
        }
        return PageResponse.of(flightRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    private static Specification<FlightEntity> toSpecification(FlightFilter filter) {
        List<Specification<FlightEntity>> specs = new ArrayList<>();
        if (filter.departureFrom() != null) {
            LocalDateTime from = filter.departureFrom().atStartOfDay();
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDateTime>get("boardingDate"), from));
        }
        if (filter.departureTo() != null) {
            // Fim do dia inclusivo: < dia seguinte 00:00
            LocalDateTime toExclusive = filter.departureTo().plusDays(1).atStartOfDay();
            specs.add((root, query, cb) -> cb.lessThan(root.<LocalDateTime>get("boardingDate"), toExclusive));
        }
        if (filter.originCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("origin").get("id"), filter.originCityId()));
        }
        if (filter.destinationCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("destination").get("id"), filter.destinationCityId()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Specs.hasText(filter.q())) {
            specs.add(Specs.containsIgnoreCase("companyName", filter.q()));
        }
        return Specification.allOf(specs);
    }

    @Transactional(readOnly = true)
    public FlightResponse findById(UUID id) {
        return toResponse(getFlightOrThrow(id));
    }

    public FlightResponse create(FlightRequest request) {
        validateFlightSchedule(request);
        FlightEntity flight = new FlightEntity();
        apply(flight, request);
        return toResponse(flightRepository.save(flight));
    }

    public FlightResponse update(UUID id, FlightRequest request) {
        validateFlightSchedule(request);
        FlightEntity flight = getFlightOrThrow(id);
        apply(flight, request);
        return toResponse(flightRepository.save(flight));
    }

    public void delete(UUID id) {
        FlightEntity flight = getFlightOrThrow(id);
        flightRepository.delete(flight);
        flightRepository.flush();
    }

    private static void validateFlightSchedule(FlightRequest request) {
        if (!request.returnDate().isAfter(request.boardingDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "returnDate must be after boardingDate");
        }
        if (request.originCityId().equals(request.destinationCityId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "originCityId and destinationCityId must be different");
        }
    }

    private FlightEntity getFlightOrThrow(UUID id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found: " + id));
    }

    private void apply(FlightEntity flight, FlightRequest request) {
        flight.setCompanyName(request.companyName().trim());
        flight.setBoardingDate(request.boardingDate());
        flight.setReturnDate(request.returnDate());
        flight.setBoardingAirport(request.boardingAirport().trim());
        flight.setReturnAirport(request.returnAirport().trim());
        flight.setOrigin(findCity(request.originCityId()));
        flight.setDestination(findCity(request.destinationCityId()));
        flight.setPricePerPassenger(request.pricePerPassenger());
        flight.setCurrency(Currencies.resolve(request.currency(), flight.getCurrency()));
        flight.setSeatsAvailable(request.seatsAvailable());
        flight.setStatus(request.status());
    }

    private CityEntity findCity(UUID id) {
        CityEntity city = entityManager.find(CityEntity.class, id);
        if (city == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "City not found: " + id);
        }
        return city;
    }

    private FlightResponse toResponse(FlightEntity flight) {
        return new FlightResponse(
                flight.getId(),
                flight.getCompanyName(),
                flight.getBoardingDate(),
                flight.getReturnDate(),
                flight.getBoardingAirport(),
                flight.getReturnAirport(),
                flight.getOrigin().getId(),
                flight.getDestination().getId(),
                flight.getPricePerPassenger(),
                flight.getCurrency(),
                flight.getSeatsAvailable(),
                flight.getStatus());
    }
}



