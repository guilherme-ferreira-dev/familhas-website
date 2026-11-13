package br.com.familhas.trip;

import java.time.LocalDate;

import br.com.familhas.booking.BookingStatus;

/**
 * Futura ou passada (ADR 0010, D2). Passada: COMPLETED ou último dia antes de hoje (fuso de negócio).
 * A mesma regra está em JPQL em {@code BookingRepository.existsPastTrip}: mudar as duas juntas.
 */
public enum TripTiming {
    UPCOMING,
    PAST;

    public static TripTiming of(BookingStatus status, LocalDate endDate, LocalDate today) {
        if (status == BookingStatus.COMPLETED || (endDate != null && endDate.isBefore(today))) {
            return PAST;
        }
        return UPCOMING;
    }
}
