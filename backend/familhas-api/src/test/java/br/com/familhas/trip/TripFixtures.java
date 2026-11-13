package br.com.familhas.trip;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingItemEntity;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.category.CategoryEntity;
import br.com.familhas.city.CityEntity;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.travelPackage.TravelPackageEntity;

/** Entidades em memória para testes de viagens e recomendação (sem banco). */
public final class TripFixtures {

    private TripFixtures() {
    }

    public static CityEntity city(String name, String country) {
        CityEntity city = new CityEntity();
        city.setId(UUID.randomUUID());
        city.setName(name);
        city.setCountry(country);
        return city;
    }

    public static CategoryEntity category(String title) {
        CategoryEntity category = new CategoryEntity();
        category.setId(UUID.randomUUID());
        category.setTitle(title);
        return category;
    }

    public static TravelPackageEntity travelPackage(String title, CategoryEntity category, CityEntity destination,
            LocalDateTime departureAt) {
        FlightEntity flight = new FlightEntity();
        flight.setId(UUID.randomUUID());
        flight.setBoardingDate(departureAt);
        flight.setReturnDate(departureAt.plusDays(7));
        flight.setStatus(EntityStatus.ACTIVE);
        TravelPackageEntity travelPackage = new TravelPackageEntity();
        travelPackage.setId(UUID.randomUUID());
        travelPackage.setTitle(title);
        travelPackage.setPrice(new BigDecimal("3000.00"));
        travelPackage.setCurrency("BRL");
        travelPackage.setStatus(EntityStatus.ACTIVE);
        travelPackage.setCategory(category);
        travelPackage.setDestination(destination);
        travelPackage.setOrigin(city("Sao Paulo", "Brasil"));
        travelPackage.setFlight(flight);
        return travelPackage;
    }

    public static ClientEntity client() {
        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID());
        return client;
    }

    /** Reserva no status pedido, com um item de voo entre start e end. */
    public static BookingEntity booking(ClientEntity client, TravelPackageEntity travelPackage, BookingStatus status,
            LocalDate start, LocalDate end) {
        BookingEntity booking = new BookingEntity();
        booking.setId(UUID.randomUUID());
        booking.setClient(client);
        booking.setTravelPackage(travelPackage);
        booking.setPassengers(2);
        booking.setCurrency("BRL");
        booking.setTotalAmount(new BigDecimal("6000.00"));
        BookingItemEntity item = new BookingItemEntity();
        item.setItemType(PriceItemType.FLIGHT);
        item.setDescription("GOL - GRU / MCZ");
        item.setStartDate(start);
        item.setEndDate(end);
        item.setQuantity(2);
        booking.addItem(item);
        ReflectionTestUtils.setField(booking, "status", status);
        if (status != BookingStatus.PENDING_PAYMENT) {
            ReflectionTestUtils.setField(booking, "confirmedAt", Instant.parse("2026-11-01T12:00:00Z"));
        }
        return booking;
    }
}
