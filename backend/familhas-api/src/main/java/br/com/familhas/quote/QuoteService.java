package br.com.familhas.quote;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.client.ClientRepository;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.pricing.PriceBreakdown;
import br.com.familhas.pricing.PriceComponent;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.pricing.PricingRequest;
import br.com.familhas.pricing.PricingService;
import br.com.familhas.provider.ProviderGateway;
import br.com.familhas.provider.ProviderOffer;
import br.com.familhas.provider.car.CarRentalOffer;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.car.CuratedCarRentalProvider;
import br.com.familhas.provider.flight.CuratedFlightProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.hotel.CuratedHotelProvider;
import br.com.familhas.provider.hotel.HotelOffer;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.quote.dto.QuoteRequest;
import br.com.familhas.quote.dto.QuoteResponse;
import br.com.familhas.quote.dto.QuoteSummaryResponse;
import br.com.familhas.search.CatalogSearchService;
import br.com.familhas.travelPackage.TravelPackageComponentEntity;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;

/**
 * Cotação (guia, Fase 5; ADR 0006). Os preços são sempre reconsultados no provedor (findOffer, sem cache) e
 * calculados pelo PricingService; o resultado vira um snapshot com validade.
 *
 * - Pacote: voo do pacote, hotel e carro dos componentes (noites e dias do cadastro, a partir do embarque),
 *   promoção = desconto percentual equivalente a price -> pricePromotion.
 * - Avulso: ofertas escolhidas na busca, com as datas informadas.
 */
@Service
public class QuoteService {

    static final Set<String> SORTABLE = Set.of("createdAt", "expiresAt", "totalAmount");

    private final ProviderGateway gateway;
    private final List<FlightProvider> flightProviders;
    private final List<HotelProvider> hotelProviders;
    private final List<CarRentalProvider> carProviders;
    private final PricingService pricingService;
    private final TravelPackageRepository travelPackageRepository;
    private final ClientRepository clientRepository;
    private final QuoteRepository quoteRepository;
    private final QuoteProperties properties;
    private final Clock clock;

    public QuoteService(ProviderGateway gateway, List<FlightProvider> flightProviders,
            List<HotelProvider> hotelProviders, List<CarRentalProvider> carProviders, PricingService pricingService,
            TravelPackageRepository travelPackageRepository, ClientRepository clientRepository,
            QuoteRepository quoteRepository, QuoteProperties properties, Clock clock) {
        this.gateway = gateway;
        this.flightProviders = flightProviders;
        this.hotelProviders = hotelProviders;
        this.carProviders = carProviders;
        this.pricingService = pricingService;
        this.travelPackageRepository = travelPackageRepository;
        this.clientRepository = clientRepository;
        this.quoteRepository = quoteRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Item montado antes do cálculo: componente de preço + dados que ficam no snapshot. */
    record DraftItem(PriceComponent component, String provider, String offerId, LocalDate startDate,
            LocalDate endDate) {
    }

    @Transactional
    public QuoteResponse create(UUID clientId, QuoteRequest request) {
        if (request.travelPackageId() != null && request.hasItems()) {
            throw badRequest("Informe travelPackageId ou itens avulsos (flight, hotel, car), nao os dois");
        }
        if (request.travelPackageId() == null && !request.hasItems()) {
            throw badRequest("Informe travelPackageId ou ao menos um item (flight, hotel, car)");
        }
        int passengers = request.passengers();

        TravelPackageEntity travelPackage = null;
        List<DraftItem> drafts;
        BigDecimal promotionPercent = BigDecimal.ZERO;
        if (request.travelPackageId() != null) {
            travelPackage = travelPackageRepository.findById(request.travelPackageId())
                    .filter(p -> p.getStatus() == EntityStatus.ACTIVE)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Travel package not found: " + request.travelPackageId()));
            drafts = packageItems(travelPackage, passengers);
            promotionPercent = PricingService.promotionPercentOf(travelPackage.getPrice(),
                    travelPackage.getPricePromotion());
        } else {
            drafts = selectedItems(request, passengers);
        }

        PriceBreakdown breakdown = pricingService.price(new PricingRequest(
                drafts.stream().map(DraftItem::component).toList(), passengers, promotionPercent));

        QuoteEntity quote = new QuoteEntity();
        quote.setClient(clientRepository.getReferenceById(clientId));
        quote.setTravelPackage(travelPackage);
        quote.setStatus(QuoteStatus.OPEN);
        quote.setPassengers(passengers);
        quote.setCurrency(breakdown.currency());
        quote.setComponentsTotal(breakdown.componentsTotal().amount());
        quote.setMarginPercent(breakdown.marginPercent());
        quote.setMarginAmount(breakdown.margin().amount());
        quote.setPromotionPercent(breakdown.promotionPercent());
        quote.setDiscountAmount(breakdown.discount().amount());
        quote.setServiceFeeAmount(breakdown.serviceFee().amount());
        quote.setTotalAmount(breakdown.total().amount());
        quote.setExpiresAt(clock.instant().plus(properties.validity()));
        for (int i = 0; i < drafts.size(); i++) {
            quote.addItem(toEntity(drafts.get(i), breakdown.items().get(i)));
        }
        return toResponse(quoteRepository.saveAndFlush(quote));
    }

    @Transactional
    public QuoteResponse findForClient(UUID clientId, UUID quoteId) {
        QuoteEntity quote = quoteRepository.findByIdAndClientId(quoteId, clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found: " + quoteId));
        quote.expireIfDue(clock.instant());
        return toResponse(quote);
    }

    /** Lista do cliente. O status EXPIRED é calculado na leitura, sem alterar o banco. */
    @Transactional(readOnly = true)
    public PageResponse<QuoteSummaryResponse> findAllForClient(UUID clientId, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        Instant now = clock.instant();
        return PageResponse.of(quoteRepository.findByClientId(clientId, pageable), quote -> new QuoteSummaryResponse(
                quote.getId(),
                quote.getStatus() == QuoteStatus.OPEN && !now.isBefore(quote.getExpiresAt())
                        ? QuoteStatus.EXPIRED : quote.getStatus(),
                quote.getTravelPackage() == null ? null : quote.getTravelPackage().getId(),
                quote.getPassengers(), quote.getCurrency(), quote.getTotalAmount(), quote.getExpiresAt(),
                quote.getCreatedAt()));
    }

    private List<DraftItem> packageItems(TravelPackageEntity travelPackage, int passengers) {
        List<DraftItem> drafts = new ArrayList<>();
        FlightOffer flight = requireOffer(gateway.findOffer(flightProviders,
                CuratedFlightProvider.offerIdOf(travelPackage.getFlight().getId())), "voo do pacote");
        drafts.add(flightItem(flight, passengers));
        LocalDate start = flight.departureAt().toLocalDate();
        for (TravelPackageComponentEntity component : travelPackage.getComponents()) {
            int quantity = component.getQuantity();
            if (component.getComponentType() == PackageComponentType.HOTEL_STAY) {
                HotelOffer room = requireOffer(gateway.findOffer(hotelProviders,
                        CuratedHotelProvider.offerIdOf(component.getRoomOffer().getId())), "hotel do pacote");
                drafts.add(hotelItem(room, passengers, start, start.plusDays(quantity)));
            } else if (component.getComponentType() == PackageComponentType.CAR_RENTAL) {
                CarRentalOffer car = requireOffer(gateway.findOffer(carProviders,
                        CuratedCarRentalProvider.offerIdOf(component.getCarRentalOffer().getId())), "carro do pacote");
                drafts.add(carItem(car, passengers, start, start.plusDays(quantity)));
            }
        }
        return drafts;
    }

    private List<DraftItem> selectedItems(QuoteRequest request, int passengers) {
        List<DraftItem> drafts = new ArrayList<>();
        if (request.flight() != null) {
            FlightOffer flight = requireOffer(gateway.findOffer(flightProviders, request.flight().offerId()), "voo");
            drafts.add(flightItem(flight, passengers));
        }
        if (request.hotel() != null) {
            HotelOffer room = requireOffer(gateway.findOffer(hotelProviders, request.hotel().offerId()), "hotel");
            drafts.add(hotelItem(room, passengers, request.hotel().checkIn(), request.hotel().checkOut()));
        }
        if (request.car() != null) {
            CarRentalOffer car = requireOffer(gateway.findOffer(carProviders, request.car().offerId()), "carro");
            drafts.add(carItem(car, passengers, request.car().pickUpDate(), request.car().dropOffDate()));
        }
        return drafts;
    }

    private DraftItem flightItem(FlightOffer flight, int passengers) {
        LocalDate departure = flight.departureAt().toLocalDate();
        notInThePast(departure);
        if (flight.seatsAvailable() < passengers) {
            throw unprocessable("O voo tem " + flight.seatsAvailable() + " assento(s) para " + passengers
                    + " passageiro(s)");
        }
        String description = flight.companyName() + " - " + flight.boardingAirport() + " / " + flight.returnAirport();
        return new DraftItem(new PriceComponent(PriceItemType.FLIGHT, description, flight.pricePerPassenger(),
                passengers), flight.provider(), flight.offerId(), departure, flight.returnAt().toLocalDate());
    }

    private DraftItem hotelItem(HotelOffer room, int passengers, LocalDate checkIn, LocalDate checkOut) {
        notInThePast(checkIn);
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1 || nights > 30) {
            throw badRequest("checkOut must be 1 to 30 nights after checkIn");
        }
        int rooms = CatalogSearchService.roomsFor(passengers, room.maxGuests());
        String description = room.hotelName() + " - " + room.roomType() + " (" + rooms + " quarto(s) x " + nights
                + " noite(s))";
        return new DraftItem(new PriceComponent(PriceItemType.HOTEL_STAY, description, room.pricePerNight(),
                rooms * nights), room.provider(), room.offerId(), checkIn, checkOut);
    }

    private DraftItem carItem(CarRentalOffer car, int passengers, LocalDate pickUp, LocalDate dropOff) {
        notInThePast(pickUp);
        if (dropOff.isBefore(pickUp)) {
            throw badRequest("dropOffDate must be on or after pickUpDate");
        }
        if (car.seats() < passengers) {
            throw unprocessable("O carro tem " + car.seats() + " lugares para " + passengers + " passageiro(s)");
        }
        int days = CatalogSearchService.rentalDays(pickUp, dropOff);
        String description = car.companyName() + " - " + car.carModel() + " (" + days + " diaria(s))";
        return new DraftItem(new PriceComponent(PriceItemType.CAR_RENTAL, description, car.pricePerDay(), days),
                car.provider(), car.offerId(), pickUp, dropOff);
    }

    private static <O extends ProviderOffer> O requireOffer(Optional<O> offer, String what) {
        return offer.orElseThrow(() -> unprocessable("Oferta de " + what + " nao esta mais disponivel"));
    }

    private void notInThePast(LocalDate date) {
        if (date.isBefore(BusinessTime.today(clock))) {
            throw badRequest("Datas da viagem nao podem estar no passado");
        }
    }

    private static QuoteItemEntity toEntity(DraftItem draft, PriceBreakdown.PricedItem priced) {
        QuoteItemEntity item = new QuoteItemEntity();
        item.setItemType(priced.type());
        item.setProvider(draft.provider());
        item.setOfferId(draft.offerId());
        item.setDescription(truncate(priced.description(), 300));
        item.setStartDate(draft.startDate());
        item.setEndDate(draft.endDate());
        item.setUnitPrice(priced.unitPrice().amount());
        item.setCurrency(priced.unitPrice().currency());
        item.setQuantity(priced.quantity());
        item.setSubtotal(priced.subtotal().amount());
        return item;
    }

    static QuoteResponse toResponse(QuoteEntity quote) {
        List<QuoteResponse.Item> items = quote.getItems().stream()
                .map(i -> new QuoteResponse.Item(i.getItemType(), i.getDescription(), i.getStartDate(),
                        i.getEndDate(), i.getQuantity()))
                .toList();
        return new QuoteResponse(
                quote.getId(),
                quote.getStatus(),
                quote.getTravelPackage() == null ? null : quote.getTravelPackage().getId(),
                quote.getPassengers(),
                items,
                quote.getCurrency(),
                quote.getComponentsTotal().add(quote.getMarginAmount()),
                quote.getDiscountAmount(),
                quote.getServiceFeeAmount(),
                quote.getTotalAmount(),
                quote.getExpiresAt(),
                quote.getCreatedAt());
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException unprocessable(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }
}
