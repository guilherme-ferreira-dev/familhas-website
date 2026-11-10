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
import br.com.familhas.hotel.dto.HotelFilter;
import br.com.familhas.hotel.dto.HotelRequest;
import br.com.familhas.hotel.dto.HotelResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    /** Filtros: cityId, minStars, status, q. Ordenação: name, stars, createdAt, updatedAt. */
    @GetMapping
    public PageResponse<HotelResponse> findAll(
            @Valid @ModelAttribute HotelFilter filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return hotelService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public HotelResponse findById(@PathVariable UUID id) {
        return hotelService.findById(id);
    }

    @PostMapping
    public ResponseEntity<HotelResponse> create(@RequestBody @Valid HotelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotelService.create(request));
    }

    @PutMapping("/{id}")
    public HotelResponse update(@PathVariable UUID id, @RequestBody @Valid HotelRequest request) {
        return hotelService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
