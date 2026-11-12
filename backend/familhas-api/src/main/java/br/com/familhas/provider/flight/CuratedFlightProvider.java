package br.com.familhas.provider.flight;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.common.Money;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.provider.ProviderOffer;

/** Voos cadastrados pelo Enéas (tabela flights). Só ACTIVE e com assentos para todos os passageiros. */
@Component
public class CuratedFlightProvider implements FlightProvider {

    public static final String NAME = "curated";

    private final FlightRepository repository;

    public CuratedFlightProvider(FlightRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightOffer> search(FlightSearchCriteria criteria) {
        List<Specification<FlightEntity>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("status"), EntityStatus.ACTIVE));
        specs.add((root, query, cb) -> cb.equal(root.get("origin").get("id"), criteria.originCityId()));
        specs.add((root, query, cb) -> cb.equal(root.get("destination").get("id"), criteria.destinationCityId()));
        specs.add(onDay("boardingDate", criteria.departureDate()));
        if (criteria.returnDate() != null) {
            specs.add(onDay("returnDate", criteria.returnDate()));
        }
        specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("seatsAvailable"), criteria.passengers()));
        return repository.findAll(Specification.allOf(specs)).stream().map(CuratedFlightProvider::toOffer).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlightOffer> findOffer(String offerId) {
        return parse(offerId).flatMap(repository::findById)
                .filter(flight -> flight.getStatus() == EntityStatus.ACTIVE)
                .map(CuratedFlightProvider::toOffer);
    }

    public static String offerIdOf(UUID flightId) {
        return ProviderOffer.id(NAME, flightId.toString());
    }

    static Optional<UUID> parse(String offerId) {
        if (!NAME.equals(ProviderOffer.providerOf(offerId))) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(ProviderOffer.localIdOf(offerId)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static Specification<FlightEntity> onDay(String attribute, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.<LocalDateTime>get(attribute), start),
                cb.lessThan(root.<LocalDateTime>get(attribute), end));
    }

    static FlightOffer toOffer(FlightEntity flight) {
        return new FlightOffer(
                offerIdOf(flight.getId()),
                NAME,
                flight.getCompanyName(),
                flight.getOrigin().getId(),
                flight.getDestination().getId(),
                flight.getBoardingDate(),
                flight.getReturnDate(),
                flight.getBoardingAirport(),
                flight.getReturnAirport(),
                new Money(flight.getPricePerPassenger(), flight.getCurrency()),
                flight.getSeatsAvailable());
    }
}
