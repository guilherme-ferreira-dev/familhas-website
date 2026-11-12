package br.com.familhas.provider.hotel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.common.Money;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.hotel.RoomOfferEntity;
import br.com.familhas.hotel.RoomOfferRepository;
import br.com.familhas.provider.ProviderOffer;

/**
 * Quartos cadastrados pelo Enéas. O catálogo curado não controla disponibilidade por data: todo quarto ativo
 * de hotel ativo na cidade conta como disponível; as datas só definem o número de noites (ADR 0006).
 * Grupos maiores que maxGuests ocupam mais de um quarto (o preço considera isso).
 */
@Component
public class CuratedHotelProvider implements HotelProvider {

    public static final String NAME = "curated";

    private final RoomOfferRepository repository;

    public CuratedHotelProvider(RoomOfferRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelOffer> search(HotelSearchCriteria criteria) {
        Specification<RoomOfferEntity> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EntityStatus.ACTIVE),
                cb.equal(root.get("hotel").get("status"), EntityStatus.ACTIVE),
                cb.equal(root.get("hotel").get("city").get("id"), criteria.cityId()));
        return repository.findAll(spec).stream().map(CuratedHotelProvider::toOffer).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HotelOffer> findOffer(String offerId) {
        return parse(offerId).flatMap(repository::findById)
                .filter(room -> room.getStatus() == EntityStatus.ACTIVE
                        && room.getHotel().getStatus() == EntityStatus.ACTIVE)
                .map(CuratedHotelProvider::toOffer);
    }

    public static String offerIdOf(UUID roomOfferId) {
        return ProviderOffer.id(NAME, roomOfferId.toString());
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

    static HotelOffer toOffer(RoomOfferEntity room) {
        return new HotelOffer(
                offerIdOf(room.getId()),
                NAME,
                room.getHotel().getName(),
                room.getHotel().getCity().getId(),
                room.getHotel().getStars(),
                room.getRoomType(),
                room.getBoardType(),
                room.getMaxGuests(),
                new Money(room.getPricePerNight(), room.getCurrency()));
    }
}
