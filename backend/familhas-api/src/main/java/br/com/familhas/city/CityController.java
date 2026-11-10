package br.com.familhas.city;

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

import br.com.familhas.city.dto.CityFilter;
import br.com.familhas.city.dto.CityRequest;
import br.com.familhas.city.dto.CityResponse;
import br.com.familhas.common.PageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/cities")
public class CityController {

    private final CityService cityService;

    public CityController(CityService cityService) {
        this.cityService = cityService;
    }

    /** Filtros: cityType, status, q. Ordenação: name, country, createdAt, updatedAt. */
    @GetMapping
    public PageResponse<CityResponse> findAll(
            @Valid @ModelAttribute CityFilter filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return cityService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public CityResponse findById(@PathVariable UUID id) {
        return cityService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CityResponse> create(@RequestBody @Valid CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.create(request));
    }

    @PutMapping("/{id}")
    public CityResponse update(@PathVariable UUID id, @RequestBody @Valid CityRequest request) {
        return cityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        cityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



