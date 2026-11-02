package br.com.familhas.carRental;

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

import br.com.familhas.carRental.dto.CarRentalOfferFilter;
import br.com.familhas.carRental.dto.CarRentalOfferRequest;
import br.com.familhas.carRental.dto.CarRentalOfferResponse;
import br.com.familhas.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/car-rental-offers")
public class CarRentalOfferController {

    private final CarRentalOfferService carRentalOfferService;

    public CarRentalOfferController(CarRentalOfferService carRentalOfferService) {
        this.carRentalOfferService = carRentalOfferService;
    }

    /**
     * Filtros: cityId, carCategory, transmission, minSeats, maxPricePerDay, status.
     * Ordenação: pricePerDay, seats, companyName, createdAt, updatedAt.
     */
    @GetMapping
    public PageResponse<CarRentalOfferResponse> findAll(
            @Valid @ModelAttribute CarRentalOfferFilter filter,
            @PageableDefault(size = 20, sort = "pricePerDay", direction = Sort.Direction.ASC) Pageable pageable) {
        return carRentalOfferService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public CarRentalOfferResponse findById(@PathVariable UUID id) {
        return carRentalOfferService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CarRentalOfferResponse> create(@RequestBody @Valid CarRentalOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carRentalOfferService.create(request));
    }

    @PutMapping("/{id}")
    public CarRentalOfferResponse update(@PathVariable UUID id, @RequestBody @Valid CarRentalOfferRequest request) {
        return carRentalOfferService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        carRentalOfferService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
