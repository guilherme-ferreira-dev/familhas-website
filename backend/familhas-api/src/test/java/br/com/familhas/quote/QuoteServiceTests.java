package br.com.familhas.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.carRental.CarRentalOfferEntity;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.common.Money;
import br.com.familhas.enums.BoardType;
import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.enums.Transmission;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.hotel.RoomOfferEntity;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.api.BusinessException;
import br.com.familhas.pricing.PricingProperties;
import br.com.familhas.pricing.PricingService;
import br.com.familhas.provider.ProviderGateway;
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
import br.com.familhas.quote.dto.QuoteRequest.CarSelection;
import br.com.familhas.quote.dto.QuoteRequest.FlightSelection;
import br.com.familhas.quote.dto.QuoteRequest.HotelSelection;
import br.com.familhas.quote.dto.QuoteResponse;
import br.com.familhas.travelPackage.TravelPackageComponentEntity;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;

class QuoteServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");
    private static final UUID FLIGHT_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID CAR_ID = UUID.randomUUID();
    private static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);

    private final ProviderGateway gateway = mock(ProviderGateway.class);
    private final List<FlightProvider> flightProviders = List.of(mock(FlightProvider.class));
    private final List<HotelProvider> hotelProviders = List.of(mock(HotelProvider.class));
    private final List<CarRentalProvider> carProviders = List.of(mock(CarRentalProvider.class));
    private final TravelPackageRepository packages = mock(TravelPackageRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private QuoteService service;

    @BeforeEach
    void setUp() {
        service = new QuoteService(gateway, flightProviders, hotelProviders, carProviders,
                new PricingService(new PricingProperties(BigDecimal.TEN, BigDecimal.ZERO, "BRL")), packages, clients,
                quotes, new QuoteProperties(Duration.ofHours(24)), Clock.fixed(NOW, ZoneOffset.UTC));
        when(clients.getReferenceById(CLIENT)).thenReturn(new ClientEntity());
        when(quotes.saveAndFlush(any(QuoteEntity.class))).thenAnswer(i -> i.getArgument(0));
        stubOffer(flightProviders, flight("899.90", 120));
        stubOffer(hotelProviders, room("890.00", "BRL"));
        stubOffer(carProviders, car(5));
    }

    @Test
    void separateItemsArePricedRepricedAtTheProviderAndSnapshotted() {
        QuoteResponse response = service.create(CLIENT, new QuoteRequest(null, 2,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_ID)),
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ID), DEPARTURE, DEPARTURE.plusDays(3)),
                new CarSelection(CuratedCarRentalProvider.offerIdOf(CAR_ID), DEPARTURE, DEPARTURE.plusDays(3))));

        QuoteEntity saved = savedQuote();
        assertThat(saved.getComponentsTotal()).isEqualByComparingTo("4829.50");
        assertThat(saved.getMarginAmount()).isEqualByComparingTo("482.95");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("5312.45");
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(saved.getItems()).extracting(QuoteItemEntity::getItemType)
                .containsExactly(PriceItemType.FLIGHT, PriceItemType.HOTEL_STAY, PriceItemType.CAR_RENTAL);
        assertThat(saved.getItems().get(1).getUnitPrice()).isEqualByComparingTo("890.00");

        // O cliente vê subtotal com margem, sem custo por item.
        assertThat(response.status()).isEqualTo(QuoteStatus.OPEN);
        assertThat(response.subtotal()).isEqualByComparingTo("5312.45");
        assertThat(response.total()).isEqualByComparingTo("5312.45");
        assertThat(response.items().get(1).quantity()).isEqualTo(3);
        assertThat(response.items().get(1).description()).contains("1 quarto(s) x 3 noite(s)");
    }

    @Test
    void packageQuoteUsesItsComponentsAndPromotion() {
        when(packages.findById(any())).thenReturn(Optional.of(maceioPackage()));

        QuoteResponse response = service.create(CLIENT, new QuoteRequest(UUID.randomUUID(), 2, null, null, null));

        QuoteEntity saved = savedQuote();
        assertThat(saved.getPromotionPercent()).isEqualByComparingTo("15.1520");
        assertThat(saved.getComponentsTotal()).isEqualByComparingTo("8389.50");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("1398.29");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("7830.16");
        assertThat(response.items()).extracting(QuoteResponse.Item::endDate)
                .containsExactly(DEPARTURE.plusDays(7), DEPARTURE.plusDays(7), DEPARTURE.plusDays(3));
    }

    @Test
    void manualQuoteAppliesTheAdminDiscountAndKeepsItSeparate() {
        service.createManual(CLIENT, separateItems(), new ManualQuoteTerms(new BigDecimal("100.00"),
                Duration.ofHours(72), "  Negociado no WhatsApp  ", "admin.familhas"));

        QuoteEntity saved = savedQuote();
        assertThat(saved.getOrigin()).isEqualTo(QuoteOrigin.ADMIN);
        assertThat(saved.getManualDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("5212.45");
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));
        assertThat(saved.getInternalNote()).isEqualTo("Negociado no WhatsApp");
        assertThat(saved.getCreatedBy()).isEqualTo("admin.familhas");
    }

    @Test
    void manualDiscountCanTakeTheWholeMarginButNeverGoBelowCost() {
        // Margem de 10% sobre 4829.50 = 482.95: total mínimo = custo.
        service.createManual(CLIENT, separateItems(), new ManualQuoteTerms(new BigDecimal("482.95"), null, null, "a"));
        assertThat(savedQuote().getTotalAmount()).isEqualByComparingTo("4829.50");

        assertThatThrownBy(() -> service.createManual(CLIENT, separateItems(),
                new ManualQuoteTerms(new BigDecimal("482.96"), null, null, "a")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.code()).isEqualTo("QUOTE_BELOW_COST");
                    assertThat(business.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(business.getBody().getProperties()).containsEntry("maxManualDiscount",
                            new BigDecimal("482.95"));
                });
    }

    @Test
    void promotionAlreadyBeyondTheMarginLeavesNoRoomForManualDiscount() {
        when(packages.findById(any())).thenReturn(Optional.of(maceioPackage()));
        QuoteRequest request = new QuoteRequest(UUID.randomUUID(), 2, null, null, null);

        assertThatThrownBy(() -> service.createManual(CLIENT, request,
                new ManualQuoteTerms(new BigDecimal("0.01"), null, null, "a")))
                .isInstanceOf(BusinessException.class);

        service.createManual(CLIENT, request, new ManualQuoteTerms(null, null, null, "a"));
        assertThat(savedQuote().getTotalAmount()).isEqualByComparingTo("7830.16");
        assertThat(savedQuote().getOrigin()).isEqualTo(QuoteOrigin.ADMIN);
    }

    @Test
    void customerQuoteHasNoManualTerms() {
        service.create(CLIENT, separateItems());

        assertThat(savedQuote().getOrigin()).isEqualTo(QuoteOrigin.CUSTOMER);
        assertThat(savedQuote().getManualDiscountAmount()).isEqualByComparingTo("0");
    }

    private static QuoteRequest separateItems() {
        return new QuoteRequest(null, 2,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_ID)),
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ID), DEPARTURE, DEPARTURE.plusDays(3)),
                new CarSelection(CuratedCarRentalProvider.offerIdOf(CAR_ID), DEPARTURE, DEPARTURE.plusDays(3)));
    }

    @Test
    void packageAndSeparateItemsTogetherOrNothingIsBadRequest() {
        assertStatus(() -> service.create(CLIENT, new QuoteRequest(UUID.randomUUID(), 1,
                new FlightSelection("curated:x"), null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(CLIENT, new QuoteRequest(null, 1, null, null, null)),
                HttpStatus.BAD_REQUEST);
        verify(quotes, never()).saveAndFlush(any());
    }

    @Test
    void offerThatNoLongerExistsIsUnprocessable() {
        when(gateway.findOffer(eq(flightProviders), eq("curated:sumiu"))).thenReturn(Optional.empty());

        assertStatus(() -> service.create(CLIENT, new QuoteRequest(null, 1, new FlightSelection("curated:sumiu"),
                null, null)), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void notEnoughSeatsIsUnprocessable() {
        stubOffer(flightProviders, flight("749.00", 4));

        assertStatus(() -> service.create(CLIENT, new QuoteRequest(null, 5,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_ID)), null, null)),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void itemsInDifferentCurrenciesAreUnprocessable() {
        stubOffer(hotelProviders, room("180.00", "USD"));

        assertStatus(() -> service.create(CLIENT, new QuoteRequest(null, 2,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_ID)),
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ID), DEPARTURE, DEPARTURE.plusDays(3)), null)),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void pastHotelDatesAreBadRequest() {
        assertStatus(() -> service.create(CLIENT, new QuoteRequest(null, 1, null,
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ID), LocalDate.of(2026, 11, 1),
                        LocalDate.of(2026, 11, 3)), null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void quoteOfAnotherClientIsNotFound() {
        when(quotes.findByIdAndClientId(any(), eq(CLIENT))).thenReturn(Optional.empty());

        assertStatus(() -> service.findForClient(CLIENT, UUID.randomUUID()), HttpStatus.NOT_FOUND);
    }

    @Test
    void openQuotePastItsValidityIsReadAsExpired() {
        QuoteEntity quote = new QuoteEntity();
        quote.setStatus(QuoteStatus.OPEN);
        quote.setExpiresAt(NOW.minusSeconds(1));
        quote.setPassengers(1);
        quote.setCurrency("BRL");
        quote.setComponentsTotal(BigDecimal.TEN);
        quote.setMarginAmount(BigDecimal.ONE);
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setServiceFeeAmount(BigDecimal.ZERO);
        quote.setTotalAmount(new BigDecimal("11"));
        when(quotes.findByIdAndClientId(any(), eq(CLIENT))).thenReturn(Optional.of(quote));

        assertThat(service.findForClient(CLIENT, UUID.randomUUID()).status()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
    }

    private QuoteEntity savedQuote() {
        ArgumentCaptor<QuoteEntity> captor = ArgumentCaptor.forClass(QuoteEntity.class);
        verify(quotes).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubOffer(List providers, Object offer) {
        when(gateway.findOffer(eq(providers), any())).thenReturn(Optional.of(offer));
    }

    private static void assertStatus(Runnable action, HttpStatusCode status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }

    private static FlightOffer flight(String price, int seats) {
        return new FlightOffer(CuratedFlightProvider.offerIdOf(FLIGHT_ID), "curated", "Gol", UUID.randomUUID(),
                MACEIO, DEPARTURE.atTime(6, 20), DEPARTURE.plusDays(7).atTime(20, 45), "GRU", "MCZ",
                Money.of(price, "BRL"), seats);
    }

    private static HotelOffer room(String price, String currency) {
        return new HotelOffer(CuratedHotelProvider.offerIdOf(ROOM_ID), "curated", "Pajucara Mar Resort", MACEIO, 5,
                "Standard Duplo", BoardType.ALL_INCLUSIVE, 2, Money.of(price, currency));
    }

    private static CarRentalOffer car(int seats) {
        return new CarRentalOffer(CuratedCarRentalProvider.offerIdOf(CAR_ID), "curated", MACEIO, "Locadora Litoral",
                "Fiat Argo", CarCategory.ECONOMY, Transmission.MANUAL, seats, Money.of("119.90", "BRL"));
    }

    private static TravelPackageEntity maceioPackage() {
        FlightEntity flight = FlightEntity.builder().id(FLIGHT_ID).build();
        RoomOfferEntity room = RoomOfferEntity.builder().id(ROOM_ID).build();
        CarRentalOfferEntity car = CarRentalOfferEntity.builder().id(CAR_ID).build();
        TravelPackageEntity travelPackage = TravelPackageEntity.builder()
                .id(UUID.randomUUID()).status(EntityStatus.ACTIVE)
                .price(new BigDecimal("3299.90")).pricePromotion(new BigDecimal("2799.90"))
                .flight(flight).build();
        travelPackage.getComponents().add(TravelPackageComponentEntity.builder()
                .componentType(PackageComponentType.HOTEL_STAY).roomOffer(room).quantity(7).build());
        travelPackage.getComponents().add(TravelPackageComponentEntity.builder()
                .componentType(PackageComponentType.CAR_RENTAL).carRentalOffer(car).quantity(3).build());
        return travelPackage;
    }
}
