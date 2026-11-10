package br.com.familhas.hotel;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.common.PageResponse;
import br.com.familhas.hotel.dto.RoomOfferFilter;
import br.com.familhas.hotel.dto.RoomOfferRequest;
import br.com.familhas.hotel.dto.RoomOfferResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/room-offers")
public class RoomOfferController {

    private final RoomOfferService roomOfferService;

    public RoomOfferController(RoomOfferService roomOfferService) {
        this.roomOfferService = roomOfferService;
    }

    /**
     * Filtros: hotelId, cityId, boardType, guests, maxPricePerNight, status.
     * Ordenação: pricePerNight, maxGuests, roomType, createdAt, updatedAt.
     */
    @GetMapping
    public PageResponse<RoomOfferResponse> findAll(
            @Valid @ModelAttribute RoomOfferFilter filter,
            @PageableDefault(size = 20, sort = "pricePerNight", direction = Sort.Direction.ASC) Pageable pageable) {
        return roomOfferService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public RoomOfferResponse findById(@PathVariable UUID id) {
        return roomOfferService.findById(id);
    }

    @PostMapping
    public ResponseEntity<RoomOfferResponse> create(@RequestBody @Valid RoomOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomOfferService.create(request));
    }

    @PutMapping("/{id}")
    public RoomOfferResponse update(@PathVariable UUID id, @RequestBody @Valid RoomOfferRequest request) {
        return roomOfferService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roomOfferService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
