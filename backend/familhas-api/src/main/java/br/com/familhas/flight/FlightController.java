package br.com.familhas.flight;

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
import br.com.familhas.flight.dto.FlightFilter;
import br.com.familhas.flight.dto.FlightRequest;
import br.com.familhas.flight.dto.FlightResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    /**
     * Filtros: departureFrom, departureTo (ISO, inclusivos), q.
     * Ordenação: boardingDate, returnDate, companyName, createdAt, updatedAt.
     */
    @GetMapping
    public PageResponse<FlightResponse> findAll(
            @Valid @ModelAttribute FlightFilter filter,
            @PageableDefault(size = 20, sort = "boardingDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return flightService.findAll(filter, pageable);
    }

    @GetMapping("/{id}")
    public FlightResponse findById(@PathVariable UUID id) {
        return flightService.findById(id);
    }

    @PostMapping
    public ResponseEntity<FlightResponse> create(@RequestBody @Valid FlightRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.create(request));
    }

    @PutMapping("/{id}")
    public FlightResponse update(@PathVariable UUID id, @RequestBody @Valid FlightRequest request) {
        return flightService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        flightService.delete(id);
        return ResponseEntity.noContent().build();
    }
}



