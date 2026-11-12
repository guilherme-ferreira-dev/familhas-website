package br.com.familhas.provider.hotel;

import java.time.LocalDate;
import java.util.UUID;

public record HotelSearchCriteria(UUID cityId, LocalDate checkIn, LocalDate checkOut, int guests) {
}
