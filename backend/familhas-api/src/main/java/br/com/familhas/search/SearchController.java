package br.com.familhas.search;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.familhas.search.dto.CarSearchParams;
import br.com.familhas.search.dto.CarSearchResult;
import br.com.familhas.search.dto.FlightSearchParams;
import br.com.familhas.search.dto.FlightSearchResult;
import br.com.familhas.search.dto.HotelSearchParams;
import br.com.familhas.search.dto.HotelSearchResult;
import br.com.familhas.search.dto.PackageSearchParams;
import br.com.familhas.search.dto.PackageSearchResult;
import br.com.familhas.search.dto.SearchPage;
import jakarta.validation.Valid;

/**
 * Busca pública da Loja (GET /api/** é público, ADR 0004). Ordenação: totalPrice (padrão) e, conforme o tipo,
 * departureAt, stars ou seats.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final CatalogSearchService searchService;

    public SearchController(CatalogSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/flights")
    public SearchPage<FlightSearchResult> flights(@Valid @ModelAttribute FlightSearchParams params,
            @PageableDefault(size = 20, sort = "totalPrice", direction = Sort.Direction.ASC) Pageable pageable) {
        return searchService.searchFlights(params, pageable);
    }

    @GetMapping("/hotels")
    public SearchPage<HotelSearchResult> hotels(@Valid @ModelAttribute HotelSearchParams params,
            @PageableDefault(size = 20, sort = "totalPrice", direction = Sort.Direction.ASC) Pageable pageable) {
        return searchService.searchHotels(params, pageable);
    }

    @GetMapping("/cars")
    public SearchPage<CarSearchResult> cars(@Valid @ModelAttribute CarSearchParams params,
            @PageableDefault(size = 20, sort = "totalPrice", direction = Sort.Direction.ASC) Pageable pageable) {
        return searchService.searchCars(params, pageable);
    }

    @GetMapping("/packages")
    public SearchPage<PackageSearchResult> packages(@Valid @ModelAttribute PackageSearchParams params,
            @PageableDefault(size = 20, sort = "totalPrice", direction = Sort.Direction.ASC) Pageable pageable) {
        return searchService.searchPackages(params, pageable);
    }
}
