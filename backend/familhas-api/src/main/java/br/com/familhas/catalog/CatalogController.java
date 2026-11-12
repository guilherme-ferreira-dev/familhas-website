package br.com.familhas.catalog;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.carRental.CarRentalOfferService;
import br.com.familhas.carRental.dto.CarRentalOfferFilter;
import br.com.familhas.carRental.dto.CarRentalOfferResponse;
import br.com.familhas.category.CategoryService;
import br.com.familhas.category.dto.CategoryFilter;
import br.com.familhas.category.dto.CategoryResponse;
import br.com.familhas.city.CityService;
import br.com.familhas.city.dto.CityFilter;
import br.com.familhas.city.dto.CityResponse;
import br.com.familhas.common.PageResponse;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.FlightService;
import br.com.familhas.flight.dto.FlightFilter;
import br.com.familhas.flight.dto.FlightResponse;
import br.com.familhas.hotel.HotelService;
import br.com.familhas.hotel.RoomOfferService;
import br.com.familhas.hotel.dto.HotelFilter;
import br.com.familhas.hotel.dto.HotelResponse;
import br.com.familhas.hotel.dto.RoomOfferFilter;
import br.com.familhas.hotel.dto.RoomOfferResponse;
import br.com.familhas.travelPackage.TravelPackageService;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

import jakarta.validation.Valid;

/**
 * Catalogo publico da Loja: somente leitura e somente itens ATIVOS.
 * O status pedido pelo cliente e ignorado aqui; a administracao ve tudo em /api/admin.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final TravelPackageService travelPackageService;
    private final CategoryService categoryService;
    private final CityService cityService;
    private final FlightService flightService;
    private final HotelService hotelService;
    private final RoomOfferService roomOfferService;
    private final CarRentalOfferService carRentalOfferService;

    public CatalogController(TravelPackageService travelPackageService, CategoryService categoryService,
            CityService cityService, FlightService flightService, HotelService hotelService,
            RoomOfferService roomOfferService, CarRentalOfferService carRentalOfferService) {
        this.travelPackageService = travelPackageService;
        this.categoryService = categoryService;
        this.cityService = cityService;
        this.flightService = flightService;
        this.hotelService = hotelService;
        this.roomOfferService = roomOfferService;
        this.carRentalOfferService = carRentalOfferService;
    }

    @GetMapping("/travel-packages")
    public PageResponse<TravelPackageResponse> travelPackages(
            @Valid @ModelAttribute TravelPackageFilter filter,
            @PageableDefault(size = 20, sort = "price", direction = Sort.Direction.ASC) Pageable pageable) {
        return travelPackageService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/travel-packages/{id}")
    public TravelPackageResponse travelPackage(@PathVariable UUID id) {
        TravelPackageResponse response = travelPackageService.findById(id);
        return requireActive(id, response, response.status());
    }

    @GetMapping("/categories")
    public PageResponse<CategoryResponse> categories(
            @Valid @ModelAttribute CategoryFilter filter,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return categoryService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/categories/{id}")
    public CategoryResponse category(@PathVariable UUID id) {
        CategoryResponse response = categoryService.findById(id);
        return requireActive(id, response, response.status());
    }

    @GetMapping("/cities")
    public PageResponse<CityResponse> cities(
            @Valid @ModelAttribute CityFilter filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return cityService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/flights")
    public PageResponse<FlightResponse> flights(
            @Valid @ModelAttribute FlightFilter filter,
            @PageableDefault(size = 20, sort = "boardingDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return flightService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/hotels")
    public PageResponse<HotelResponse> hotels(
            @Valid @ModelAttribute HotelFilter filter,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return hotelService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/hotels/{id}")
    public HotelResponse hotel(@PathVariable UUID id) {
        HotelResponse response = hotelService.findById(id);
        return requireActive(id, response, response.status());
    }

    @GetMapping("/room-offers")
    public PageResponse<RoomOfferResponse> roomOffers(
            @Valid @ModelAttribute RoomOfferFilter filter,
            @PageableDefault(size = 20, sort = "pricePerNight", direction = Sort.Direction.ASC) Pageable pageable) {
        return roomOfferService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/room-offers/{id}")
    public RoomOfferResponse roomOffer(@PathVariable UUID id) {
        RoomOfferResponse response = roomOfferService.findById(id);
        return requireActive(id, response, response.status());
    }

    @GetMapping("/car-rental-offers")
    public PageResponse<CarRentalOfferResponse> carRentalOffers(
            @Valid @ModelAttribute CarRentalOfferFilter filter,
            @PageableDefault(size = 20, sort = "pricePerDay", direction = Sort.Direction.ASC) Pageable pageable) {
        return carRentalOfferService.findAll(filter.activeOnly(), pageable);
    }

    @GetMapping("/car-rental-offers/{id}")
    public CarRentalOfferResponse carRentalOffer(@PathVariable UUID id) {
        CarRentalOfferResponse response = carRentalOfferService.findById(id);
        return requireActive(id, response, response.status());
    }

    /** Item inativo responde 404 na Loja, como se nao existisse. */
    private static <T> T requireActive(UUID id, T response, EntityStatus status) {
        if (status != EntityStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found: " + id);
        }
        return response;
    }
}
