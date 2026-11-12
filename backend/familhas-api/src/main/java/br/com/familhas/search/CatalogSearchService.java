package br.com.familhas.search;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.common.BusinessTime;
import br.com.familhas.common.Money;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.pricing.PricingService;
import br.com.familhas.provider.ProviderGateway;
import br.com.familhas.provider.ProviderResult;
import br.com.familhas.provider.car.CarRentalOffer;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.car.CarRentalSearchCriteria;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.flight.FlightSearchCriteria;
import br.com.familhas.provider.hotel.HotelOffer;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.provider.hotel.HotelSearchCriteria;
import br.com.familhas.search.dto.CarSearchParams;
import br.com.familhas.search.dto.CarSearchResult;
import br.com.familhas.search.dto.FlightSearchParams;
import br.com.familhas.search.dto.FlightSearchResult;
import br.com.familhas.search.dto.HotelSearchParams;
import br.com.familhas.search.dto.HotelSearchResult;
import br.com.familhas.search.dto.PackageSearchParams;
import br.com.familhas.search.dto.PackageSearchResult;
import br.com.familhas.search.dto.SearchPage;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;

/**
 * Busca da Loja (guia, Fase 5). Voos, hotéis e carros vêm dos provedores (ProviderGateway); pacotes vêm do
 * catálogo da Familhas. Preços exibidos já têm margem; taxa de serviço e promoção entram na cotação.
 */
@Service
public class CatalogSearchService {

    static final int MAX_NIGHTS = 30;
    static final int MAX_RENTAL_DAYS = 60;

    private static final Map<String, Comparator<FlightSearchResult>> FLIGHT_SORT = Map.of(
            "totalPrice", Comparator.comparing(FlightSearchResult::totalPrice),
            "departureAt", Comparator.comparing(FlightSearchResult::departureAt));
    private static final Map<String, Comparator<HotelSearchResult>> HOTEL_SORT = Map.of(
            "totalPrice", Comparator.comparing(HotelSearchResult::totalPrice),
            "stars", Comparator.comparingInt(HotelSearchResult::stars));
    private static final Map<String, Comparator<CarSearchResult>> CAR_SORT = Map.of(
            "totalPrice", Comparator.comparing(CarSearchResult::totalPrice),
            "seats", Comparator.comparingInt(CarSearchResult::seats));
    private static final Map<String, Comparator<PackageSearchResult>> PACKAGE_SORT = Map.of(
            "totalPrice", Comparator.comparing(PackageSearchResult::totalPrice),
            "departureAt", Comparator.comparing(PackageSearchResult::departureAt));

    private final ProviderGateway gateway;
    private final List<FlightProvider> flightProviders;
    private final List<HotelProvider> hotelProviders;
    private final List<CarRentalProvider> carProviders;
    private final TravelPackageRepository travelPackageRepository;
    private final PricingService pricingService;
    private final Clock clock;

    public CatalogSearchService(ProviderGateway gateway, List<FlightProvider> flightProviders,
            List<HotelProvider> hotelProviders, List<CarRentalProvider> carProviders,
            TravelPackageRepository travelPackageRepository, PricingService pricingService, Clock clock) {
        this.gateway = gateway;
        this.flightProviders = flightProviders;
        this.hotelProviders = hotelProviders;
        this.carProviders = carProviders;
        this.travelPackageRepository = travelPackageRepository;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    public SearchPage<FlightSearchResult> searchFlights(FlightSearchParams params, Pageable pageable) {
        if (params.originCityId().equals(params.destinationCityId())) {
            throw badRequest("originCityId and destinationCityId must be different");
        }
        notInThePast("departureDate", params.departureDate());
        if (params.returnDate() != null && params.returnDate().isBefore(params.departureDate())) {
            throw badRequest("returnDate must be on or after departureDate");
        }
        validatePriceRange(params.minPrice(), params.maxPrice());
        int passengers = params.passengersOrDefault();

        ProviderResult<FlightOffer> result = gateway.search(flightProviders, new FlightSearchCriteria(
                params.originCityId(), params.destinationCityId(), params.departureDate(), params.returnDate(),
                passengers));
        List<FlightSearchResult> results = result.offers().stream()
                .map(offer -> toResult(offer, passengers))
                .filter(r -> inRange(r.totalPrice(), params.minPrice(), params.maxPrice()))
                .toList();
        return InMemoryPage.of(results, pageable, FLIGHT_SORT, "totalPrice", result.unavailableProviders());
    }

    public SearchPage<HotelSearchResult> searchHotels(HotelSearchParams params, Pageable pageable) {
        notInThePast("checkIn", params.checkIn());
        int nights = (int) ChronoUnit.DAYS.between(params.checkIn(), params.checkOut());
        if (nights < 1 || nights > MAX_NIGHTS) {
            throw badRequest("checkOut must be 1 to " + MAX_NIGHTS + " nights after checkIn");
        }
        validatePriceRange(params.minPrice(), params.maxPrice());
        int guests = params.guestsOrDefault();

        ProviderResult<HotelOffer> result = gateway.search(hotelProviders,
                new HotelSearchCriteria(params.cityId(), params.checkIn(), params.checkOut(), guests));
        List<HotelSearchResult> results = result.offers().stream()
                .filter(offer -> params.minStars() == null || offer.stars() >= params.minStars())
                .map(offer -> toResult(offer, guests, nights))
                .filter(r -> inRange(r.totalPrice(), params.minPrice(), params.maxPrice()))
                .toList();
        return InMemoryPage.of(results, pageable, HOTEL_SORT, "totalPrice", result.unavailableProviders());
    }

    public SearchPage<CarSearchResult> searchCars(CarSearchParams params, Pageable pageable) {
        notInThePast("pickUpDate", params.pickUpDate());
        if (params.dropOffDate().isBefore(params.pickUpDate())) {
            throw badRequest("dropOffDate must be on or after pickUpDate");
        }
        int days = rentalDays(params.pickUpDate(), params.dropOffDate());
        validatePriceRange(params.minPrice(), params.maxPrice());

        ProviderResult<CarRentalOffer> result = gateway.search(carProviders, new CarRentalSearchCriteria(
                params.cityId(), params.pickUpDate(), params.dropOffDate(), params.passengersOrDefault()));
        List<CarSearchResult> results = result.offers().stream()
                .filter(offer -> params.carCategory() == null || offer.carCategory() == params.carCategory())
                .map(offer -> toResult(offer, days))
                .filter(r -> inRange(r.totalPrice(), params.minPrice(), params.maxPrice()))
                .toList();
        return InMemoryPage.of(results, pageable, CAR_SORT, "totalPrice", result.unavailableProviders());
    }

    @Transactional(readOnly = true)
    public SearchPage<PackageSearchResult> searchPackages(PackageSearchParams params, Pageable pageable) {
        LocalDate today = BusinessTime.today(clock);
        LocalDate from = params.departureFrom() == null || params.departureFrom().isBefore(today)
                ? today : params.departureFrom();
        if (params.departureTo() != null && params.departureTo().isBefore(from)) {
            throw badRequest("departureTo must be on or after departureFrom (and not in the past)");
        }
        validatePriceRange(params.minPrice(), params.maxPrice());
        int passengers = params.passengersOrDefault();

        List<PackageSearchResult> results = travelPackageRepository
                .findAll(packageSpecification(params, from, passengers)).stream()
                .map(travelPackage -> toResult(travelPackage, passengers))
                .filter(r -> inRange(r.totalPrice(), params.minPrice(), params.maxPrice()))
                .toList();
        return InMemoryPage.of(results, pageable, PACKAGE_SORT, "totalPrice", List.of());
    }

    /** Diárias de carro: devolução no mesmo dia conta 1 diária. */
    static int rentalDays(LocalDate pickUp, LocalDate dropOff) {
        int days = (int) Math.max(1, ChronoUnit.DAYS.between(pickUp, dropOff));
        if (days > MAX_RENTAL_DAYS) {
            throw badRequest("Rental cannot exceed " + MAX_RENTAL_DAYS + " days");
        }
        return days;
    }

    /** Quartos necessários para o grupo, arredondando para cima. */
    static int roomsFor(int guests, int maxGuests) {
        return (guests + maxGuests - 1) / Math.max(1, maxGuests);
    }

    private FlightSearchResult toResult(FlightOffer offer, int passengers) {
        Money perPassenger = pricingService.customerPrice(offer.pricePerPassenger(), 1);
        Money total = pricingService.customerPrice(offer.pricePerPassenger(), passengers);
        return new FlightSearchResult(offer.offerId(), offer.provider(), offer.companyName(), offer.originCityId(),
                offer.destinationCityId(), offer.departureAt(), offer.returnAt(), offer.boardingAirport(),
                offer.returnAirport(), passengers, perPassenger.amount(), total.amount(), total.currency(),
                offer.seatsAvailable());
    }

    private HotelSearchResult toResult(HotelOffer offer, int guests, int nights) {
        int rooms = roomsFor(guests, offer.maxGuests());
        Money perNight = pricingService.customerPrice(offer.pricePerNight(), 1);
        Money total = pricingService.customerPrice(offer.pricePerNight(), rooms * nights);
        return new HotelSearchResult(offer.offerId(), offer.provider(), offer.hotelName(), offer.cityId(),
                offer.stars(), offer.roomType(), offer.boardType(), offer.maxGuests(), rooms, nights,
                perNight.amount(), total.amount(), total.currency());
    }

    private CarSearchResult toResult(CarRentalOffer offer, int days) {
        Money perDay = pricingService.customerPrice(offer.pricePerDay(), 1);
        Money total = pricingService.customerPrice(offer.pricePerDay(), days);
        return new CarSearchResult(offer.offerId(), offer.provider(), offer.cityId(), offer.companyName(),
                offer.carModel(), offer.carCategory(), offer.transmission(), offer.seats(), days, perDay.amount(),
                total.amount(), total.currency());
    }

    private static PackageSearchResult toResult(TravelPackageEntity travelPackage, int passengers) {
        BigDecimal perPerson = travelPackage.getPricePromotion() != null
                && travelPackage.getPricePromotion().compareTo(travelPackage.getPrice()) < 0
                        ? travelPackage.getPricePromotion()
                        : travelPackage.getPrice();
        Money unit = new Money(perPerson, travelPackage.getCurrency());
        Money total = unit.times(passengers);
        var flight = travelPackage.getFlight();
        return new PackageSearchResult(travelPackage.getId(), travelPackage.getTitle(), travelPackage.getImageUrl(),
                travelPackage.getCategory().getId(), travelPackage.getOrigin().getId(),
                travelPackage.getDestination().getId(), flight.getId(), flight.getBoardingDate(),
                flight.getReturnDate(), passengers, unit.amount(), travelPackage.getPrice(), total.amount(),
                total.currency(), flight.getSeatsAvailable());
    }

    private static Specification<TravelPackageEntity> packageSpecification(PackageSearchParams params,
            LocalDate from, int passengers) {
        List<Specification<TravelPackageEntity>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("status"), EntityStatus.ACTIVE));
        specs.add((root, query, cb) -> cb.equal(root.get("flight").get("status"), EntityStatus.ACTIVE));
        specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("flight").get("seatsAvailable"),
                passengers));
        specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(
                root.get("flight").<LocalDateTime>get("boardingDate"), from.atStartOfDay()));
        if (params.departureTo() != null) {
            LocalDateTime toExclusive = params.departureTo().plusDays(1).atStartOfDay();
            specs.add((root, query, cb) -> cb.lessThan(
                    root.get("flight").<LocalDateTime>get("boardingDate"), toExclusive));
        }
        if (params.originCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("origin").get("id"), params.originCityId()));
        }
        if (params.destinationCityId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("destination").get("id"), params.destinationCityId()));
        }
        if (params.categoryId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("category").get("id"), params.categoryId()));
        }
        return Specification.allOf(specs);
    }

    private void notInThePast(String field, LocalDate date) {
        if (date.isBefore(BusinessTime.today(clock))) {
            throw badRequest(field + " must not be in the past");
        }
    }

    private static void validatePriceRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw badRequest("minPrice must be less than or equal to maxPrice");
        }
    }

    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
