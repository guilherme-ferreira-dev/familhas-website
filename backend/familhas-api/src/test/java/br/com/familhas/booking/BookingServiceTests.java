package br.com.familhas.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.api.BusinessException;
import br.com.familhas.booking.dto.BookingRequest;
import br.com.familhas.booking.dto.BookingResponse;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.common.Money;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.payment.PaymentRepository;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.provider.ProviderGateway;
import br.com.familhas.provider.car.CarRentalProvider;
import br.com.familhas.provider.flight.CuratedFlightProvider;
import br.com.familhas.provider.flight.FlightOffer;
import br.com.familhas.provider.flight.FlightProvider;
import br.com.familhas.provider.hotel.HotelProvider;
import br.com.familhas.quote.QuoteEntity;
import br.com.familhas.quote.QuoteItemEntity;
import br.com.familhas.quote.QuoteRepository;
import br.com.familhas.quote.QuoteStatus;

class BookingServiceTests {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID FLIGHT_ID = UUID.randomUUID();
    private static final String CURATED_OFFER = CuratedFlightProvider.offerIdOf(FLIGHT_ID);

    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final FlightRepository flights = mock(FlightRepository.class);
    private final ProviderGateway gateway = mock(ProviderGateway.class);
    private final List<FlightProvider> flightProviders = List.of(mock(FlightProvider.class));
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(quotes, bookings, flights, mock(PaymentRepository.class), gateway, flightProviders,
                List.of(mock(HotelProvider.class)), List.of(mock(CarRentalProvider.class)),
                new BookingProperties(Duration.ofMinutes(30)), Clock.fixed(NOW, ZoneOffset.UTC));
        when(bookings.saveAndFlush(any(BookingEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(gateway.findOffer(eq(flightProviders), any())).thenReturn(Optional.of(flightOffer(CURATED_OFFER, 10)));
    }

    @Test
    void openQuoteBecomesPendingBookingWithTheSameSnapshotAndHeldSeats() {
        QuoteEntity quote = quote(QuoteStatus.OPEN, "BRL", NOW.plusSeconds(3600), CURATED_OFFER);
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT)).thenReturn(Optional.of(quote));
        when(flights.holdSeats(FLIGHT_ID, 2)).thenReturn(1);

        BookingResponse booking = service.create(CLIENT, new BookingRequest(QUOTE_ID));

        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.total()).isEqualByComparingTo("2200.00");
        assertThat(booking.subtotal()).isEqualByComparingTo("2200.00");
        assertThat(booking.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(booking.items()).singleElement().satisfies(i -> assertThat(i.type()).isEqualTo(PriceItemType.FLIGHT));
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.BOOKED);
        verify(flights).holdSeats(FLIGHT_ID, 2);
    }

    @Test
    void secondBookingOfTheSameQuoteIsConflictWithTheExistingBookingId() {
        UUID existing = UUID.randomUUID();
        BookingEntity booking = new BookingEntity();
        booking.setId(existing);
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.BOOKED, "BRL", NOW.plusSeconds(3600), CURATED_OFFER)));
        when(bookings.findByQuoteId(QUOTE_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.code()).isEqualTo("QUOTE_ALREADY_BOOKED");
                    assertThat(ex.getBody().getProperties()).containsEntry("bookingId", existing);
                });
        verify(flights, never()).holdSeats(any(), anyInt());
    }

    @Test
    void expiredQuoteIsConflict() {
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.OPEN, "BRL", NOW, CURATED_OFFER)));

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.code()).isEqualTo("QUOTE_EXPIRED"));
    }

    @Test
    void quoteInDollarsCannotBeBooked() {
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.OPEN, "USD", NOW.plusSeconds(3600), CURATED_OFFER)));

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.code()).isEqualTo("CURRENCY_NOT_SUPPORTED");
                });
    }

    @Test
    void noSeatsLeftIsUnprocessable() {
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.OPEN, "BRL", NOW.plusSeconds(3600), CURATED_OFFER)));
        when(flights.holdSeats(FLIGHT_ID, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("SEATS_UNAVAILABLE"));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void offerThatDisappearedIsUnprocessable() {
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.OPEN, "BRL", NOW.plusSeconds(3600), CURATED_OFFER)));
        when(gateway.findOffer(eq(flightProviders), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("OFFER_UNAVAILABLE"));
    }

    @Test
    void flightFromAnotherProviderUsesTheOfferSeatsWithoutHolding() {
        String mockOffer = "mock:flight-1";
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT))
                .thenReturn(Optional.of(quote(QuoteStatus.OPEN, "BRL", NOW.plusSeconds(3600), mockOffer)));
        when(gateway.findOffer(eq(flightProviders), eq(mockOffer))).thenReturn(Optional.of(flightOffer(mockOffer, 1)));

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.code()).isEqualTo("SEATS_UNAVAILABLE"));
        verify(flights, never()).holdSeats(any(), anyInt());
    }

    @Test
    void quoteOfAnotherClientIs404() {
        when(quotes.lockByIdAndClientId(QUOTE_ID, CLIENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(CLIENT, new BookingRequest(QUOTE_ID)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void releasingSeatsOfACancelledBookingHappensOnce() {
        BookingEntity booking = new BookingEntity();
        booking.setHeldFlightId(FLIGHT_ID);
        booking.setHeldSeats(2);

        service.releaseSeats(booking);
        service.releaseSeats(booking);

        verify(flights).releaseSeats(FLIGHT_ID, 2);
    }

    private static QuoteEntity quote(QuoteStatus status, String currency, Instant expiresAt, String flightOfferId) {
        QuoteEntity quote = new QuoteEntity();
        quote.setId(QUOTE_ID);
        quote.setClient(new ClientEntity());
        quote.setStatus(status);
        quote.setPassengers(2);
        quote.setCurrency(currency);
        quote.setComponentsTotal(new BigDecimal("2000.00"));
        quote.setMarginPercent(BigDecimal.TEN);
        quote.setMarginAmount(new BigDecimal("200.00"));
        quote.setPromotionPercent(BigDecimal.ZERO);
        quote.setDiscountAmount(BigDecimal.ZERO);
        quote.setServiceFeeAmount(BigDecimal.ZERO);
        quote.setTotalAmount(new BigDecimal("2200.00"));
        quote.setExpiresAt(expiresAt);
        QuoteItemEntity item = new QuoteItemEntity();
        item.setItemType(PriceItemType.FLIGHT);
        item.setProvider(flightOfferId.substring(0, flightOfferId.indexOf(':')));
        item.setOfferId(flightOfferId);
        item.setDescription("GOL - BSB / MCZ");
        item.setStartDate(LocalDate.of(2027, 3, 10));
        item.setEndDate(LocalDate.of(2027, 3, 17));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setCurrency(currency);
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("2000.00"));
        quote.addItem(item);
        return quote;
    }

    private static FlightOffer flightOffer(String offerId, int seats) {
        return new FlightOffer(offerId, offerId.substring(0, offerId.indexOf(':')), "GOL", UUID.randomUUID(),
                UUID.randomUUID(), LocalDateTime.of(2027, 3, 10, 8, 0), LocalDateTime.of(2027, 3, 17, 18, 0), "BSB",
                "MCZ", Money.of("1000.00", "BRL"), seats);
    }
}
