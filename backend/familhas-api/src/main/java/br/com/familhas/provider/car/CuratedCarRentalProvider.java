package br.com.familhas.provider.car;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.carRental.CarRentalOfferEntity;
import br.com.familhas.carRental.CarRentalOfferRepository;
import br.com.familhas.common.Money;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.provider.ProviderOffer;

/** Carros cadastrados pelo Enéas. Sem disponibilidade por data no catálogo curado (ADR 0006). */
@Component
public class CuratedCarRentalProvider implements CarRentalProvider {

    public static final String NAME = "curated";

    private final CarRentalOfferRepository repository;

    public CuratedCarRentalProvider(CarRentalOfferRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarRentalOffer> search(CarRentalSearchCriteria criteria) {
        Specification<CarRentalOfferEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EntityStatus.ACTIVE),
                cb.equal(root.get("city").get("id"), criteria.cityId()),
                cb.greaterThanOrEqualTo(root.get("seats"), criteria.passengers()));
        return repository.findAll(spec).stream().map(CuratedCarRentalProvider::toOffer).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CarRentalOffer> findOffer(String offerId) {
        return parse(offerId).flatMap(repository::findById)
                .filter(car -> car.getStatus() == EntityStatus.ACTIVE)
                .map(CuratedCarRentalProvider::toOffer);
    }

    public static String offerIdOf(UUID carRentalOfferId) {
        return ProviderOffer.id(NAME, carRentalOfferId.toString());
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

    static CarRentalOffer toOffer(CarRentalOfferEntity car) {
        return new CarRentalOffer(
                offerIdOf(car.getId()),
                NAME,
                car.getCity().getId(),
                car.getCompanyName(),
                car.getCarModel(),
                car.getCarCategory(),
                car.getTransmission(),
                car.getSeats(),
                new Money(car.getPricePerDay(), car.getCurrency()));
    }
}
