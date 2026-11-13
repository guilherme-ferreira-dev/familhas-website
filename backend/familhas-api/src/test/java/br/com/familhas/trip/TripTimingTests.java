package br.com.familhas.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.com.familhas.booking.BookingStatus;

class TripTimingTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @ParameterizedTest(name = "{0} terminando em {1} -> {2}")
    @CsvSource({
            "CONFIRMED, 2026-11-20, UPCOMING",
            "CONFIRMED, 2026-11-13, UPCOMING",   // último dia é hoje: ainda não acabou
            "CONFIRMED, 2026-11-12, PAST",
            "COMPLETED, 2026-11-20, PAST",       // concluída manualmente antes do fim
            "COMPLETED, 2026-01-01, PAST",
            "CONFIRMED,           , UPCOMING" }) // sem itens: não dá para dizer que passou
    void classifiesByStatusAndLastDay(BookingStatus status, LocalDate endDate, TripTiming expected) {
        assertThat(TripTiming.of(status, endDate, TODAY)).isEqualTo(expected);
    }
}
