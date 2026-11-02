package br.com.familhas.travelPackage;

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
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageRequest;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/travel-packages")
public class TravelPackageController {

    private final TravelPackageService travelPackageService;

    public TravelPackageController(TravelPackageService travelPackageService) {
        this.travelPackageService = travelPackageService;
    }

    /**
     * Filtros: originCityId, destinationCityId, categoryId, status, minPrice, maxPrice, q.
     * Paginação: page, size, sort (title, price, createdAt, updatedAt).
     */
    @GetMapping
    public PageResponse<TravelPackageResponse> findAll(
            @Valid @ModelAttribute TravelPackageFilter filter,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return travelPackageService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public TravelPackageResponse findById(@PathVariable UUID id) {
        return travelPackageService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TravelPackageResponse> create(@RequestBody @Valid TravelPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(travelPackageService.create(request));
    }

    @PutMapping("/{id}")
    public TravelPackageResponse update(@PathVariable UUID id, @RequestBody @Valid TravelPackageRequest request) {
        return travelPackageService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        travelPackageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}





