package br.com.familhas.hotel;

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

import br.com.familhas.common.Currencies;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.hotel.dto.RoomOfferFilter;
import br.com.familhas.hotel.dto.RoomOfferRequest;
import br.com.familhas.hotel.dto.RoomOfferResponse;

import jakarta.persistence.EntityManager;

@Service
@Transactional
public class RoomOfferService {

    static final Set<String> SORTABLE = Set.of("pricePerNight", "maxGuests", "roomType", "createdAt", "updatedAt");

    private final RoomOfferRepository roomOfferRepository;
    private final EntityManager entityManager;

    public RoomOfferService(RoomOfferRepository roomOfferRepository, EntityManager entityManager) {
        this.roomOfferRepository = roomOfferRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomOfferResponse> findAll(RoomOfferFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(roomOfferRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoomOfferResponse findById(UUID id) {
        return toResponse(getRoomOfferOrThrow(id));
    }

    public RoomOfferResponse create(RoomOfferRequest request) {
        RoomOfferEntity roomOffer = new RoomOfferEntity();
        apply(roomOffer, request);
        return toResponse(roomOfferRepository.save(roomOffer));
    }

    public RoomOfferResponse update(UUID id, RoomOfferRequest request) {
        RoomOfferEntity roomOffer = getRoomOfferOrThrow(id);
        apply(roomOffer, request);
        return toResponse(roomOfferRepository.save(roomOffer));
    }

    /** Oferta usada em pacote não é excluída: a FK vira 409. */
    public void delete(UUID id) {
        roomOfferRepository.delete(getRoomOfferOrThrow(id));
        roomOfferRepository.flush();
    }

    private static Specification<RoomOfferEntity> toSpecification(RoomOfferFilter filter) {
        List<Specification<RoomOfferEntity>> specs = new ArrayList<>();
        if (filter.hotelId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("hotel").get("id"), filter.hotelId()));
        }
        if (filter.cityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("hotel").get("city").get("id"), filter.cityId()));
        }
        if (filter.boardType() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("boardType"), filter.boardType()));
        }
        if (filter.guests() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("maxGuests"), filter.guests()));
        }
        if (filter.maxPricePerNight() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(
                    root.<BigDecimal>get("pricePerNight"), filter.maxPricePerNight()));
        }
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        return Specification.allOf(specs);
    }

    private RoomOfferEntity getRoomOfferOrThrow(UUID id) {
        return roomOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room offer not found: " + id));
    }

    private void apply(RoomOfferEntity roomOffer, RoomOfferRequest request) {
        HotelEntity hotel = entityManager.find(HotelEntity.class, request.hotelId());
        if (hotel == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found: " + request.hotelId());
        }
        roomOffer.setHotel(hotel);
        roomOffer.setRoomType(request.roomType().trim());
        roomOffer.setBoardType(request.boardType());
        roomOffer.setMaxGuests(request.maxGuests());
        roomOffer.setPricePerNight(request.pricePerNight());
        roomOffer.setCurrency(Currencies.resolve(request.currency(), roomOffer.getCurrency()));
        roomOffer.setStatus(request.status());
    }

    private RoomOfferResponse toResponse(RoomOfferEntity roomOffer) {
        return new RoomOfferResponse(
                roomOffer.getId(),
                roomOffer.getHotel().getId(),
                roomOffer.getRoomType(),
                roomOffer.getBoardType(),
                roomOffer.getMaxGuests(),
                roomOffer.getPricePerNight(),
                roomOffer.getCurrency(),
                roomOffer.getStatus());
    }
}
