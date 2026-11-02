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

import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.flight.dto.FlightFilter;
import br.com.familhas.flight.dto.FlightRequest;
import br.com.familhas.flight.dto.FlightResponse;

@Service
@Transactional
public class FlightService {

    static final Set<String> SORTABLE = Set.of("boardingDate", "returnDate", "companyName", "createdAt", "updatedAt");

    private final FlightRepository flightRepository;

    public FlightService(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
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
    }

    private FlightResponse toResponse(FlightEntity flight) {
        return new FlightResponse(
                flight.getId(),
                flight.getCompanyName(),
                flight.getBoardingDate(),
                flight.getReturnDate(),
                flight.getBoardingAirport(),
                flight.getReturnAirport());
    }
}



