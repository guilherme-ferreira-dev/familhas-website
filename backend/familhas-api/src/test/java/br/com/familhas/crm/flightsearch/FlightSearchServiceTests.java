package br.com.familhas.crm.flightsearch;

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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.com.familhas.api.BusinessException;
import br.com.familhas.crm.integration.IntegrationNotConfiguredException;
import br.com.familhas.crm.integration.IntegrationProperties;
import br.com.familhas.crm.integration.IntegrationRegistry;

class FlightSearchServiceTests {

    /** 16/11/2026, 12h em São Paulo. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DEC_10 = LocalDate.of(2026, 12, 10);

    private final FlightSearchGateway gateway = mock(FlightSearchGateway.class);
    private final FlightSearchRepository history = mock(FlightSearchRepository.class);
    private final FlightSearchProperties properties = new FlightSearchProperties(List.of("BSB", "CXJ"),
            "https://serpapi.com", Duration.ofSeconds(20), 40, 7, 2, Duration.ofMinutes(30));
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private FlightSearchService service(boolean configured) {
        IntegrationRegistry registry = new IntegrationRegistry(
                new IntegrationProperties("none", "none", "none", configured ? "chave" : "", "", "", ""));
        when(history.findByParamsHash(any())).thenReturn(Optional.empty());
        return new FlightSearchService(gateway, registry, properties, history, mapper, CLOCK);
    }

    @Test
    void notConfiguredGives503BeforeAnyCall() {
        assertThatThrownBy(() -> service(false).search(exact("BSB", List.of("MCZ"))))
                .isInstanceOf(IntegrationNotConfiguredException.class);
        verify(gateway, never()).roundTrip(any(), any(), any(), any(), anyInt());
        verify(history, never()).save(any());
    }

    @Test
    void exactSearchQuotesEachDestinationSeparatelyCheapestFirst() {
        when(gateway.roundTrip(eq("BSB"), eq("MCZ"), eq(DEC_10), eq(DEC_10.plusDays(5)), eq(2)))
                .thenReturn(List.of(offer(DEC_10, "2100.00"), offer(DEC_10, "1800.00")));
        when(gateway.roundTrip(eq("BSB"), eq("REC"), any(), any(), anyInt())).thenThrow(new RuntimeException("x"));

        FlightSearchResponse response = service(true).search(new FlightSearchRequest("BSB", List.of("MCZ", "REC"),
                FlightSearchRequest.Mode.EXACT, DEC_10, DEC_10.plusDays(5), null, null, 2));

        assertThat(response.destinations()).hasSize(2);
        assertThat(response.destinations().get(0).offers()).extracting(o -> o.price().toPlainString())
                .containsExactly("1800.00", "2100.00");
        assertThat(response.destinations().get(1).error()).isEqualTo("A pesquisa deste destino falhou.");
        verify(history).save(any());
    }

    @Test
    void flexibleSearchTestsDatesEveryStepAndKeepsTheCheapestPerDate() {
        when(gateway.roundTrip(any(), any(), any(), any(), anyInt())).thenAnswer(inv -> {
            LocalDate departure = inv.getArgument(2);
            LocalDate ret = inv.getArgument(3);
            assertThat(ret).isEqualTo(departure.plusDays(4));
            return List.of(offer(departure, String.valueOf(3000 - departure.getDayOfMonth())),
                    offer(departure, "9999"));
        });

        FlightSearchResponse response = service(true).search(new FlightSearchRequest("CXJ", List.of("GRU"),
                FlightSearchRequest.Mode.FLEXIBLE, null, null, List.of(YearMonth.of(2026, 12)), 4, null));

        // Dezembro: dias 1, 8, 15, 22, 29. Um preço por data, do mais barato ao mais caro.
        assertThat(response.destinations().get(0).offers()).extracting(FlightOffer::departureDate)
                .extracting(LocalDate::getDayOfMonth).containsExactly(29, 22, 15, 8, 1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "ORIGIN_NOT_ALLOWED,GRU,MCZ", "SAME_ORIGIN_DESTINATION,BSB,BSB" })
    void invalidRoutesAreRejected(String code, String origin, String destination) {
        assertCode(() -> service(true).plan(exact(origin, List.of(destination))), code);
    }

    @Test
    void invalidDatesAndTooBroadSearchesAreRejectedBeforeSpending() {
        FlightSearchService service = service(true);
        assertCode(() -> service.plan(new FlightSearchRequest("BSB", List.of("MCZ"), FlightSearchRequest.Mode.EXACT,
                LocalDate.of(2026, 11, 1), DEC_10, null, null, null)), "INVALID_DATES");
        assertCode(() -> service.plan(new FlightSearchRequest("BSB", List.of("MCZ"), FlightSearchRequest.Mode.EXACT,
                DEC_10, DEC_10.minusDays(1), null, null, null)), "INVALID_DATES");
        assertCode(() -> service.plan(new FlightSearchRequest("BSB", List.of("MCZ"), FlightSearchRequest.Mode.FLEXIBLE,
                null, null, List.of(YearMonth.of(2026, 10)), null, null)), "INVALID_DATES");
        // 7 destinos x 3 meses x ~5 datas = mais de 40 consultas.
        assertCode(() -> service.plan(new FlightSearchRequest("BSB",
                List.of("MCZ", "REC", "SSA", "FOR", "NAT", "GRU", "GIG"), FlightSearchRequest.Mode.FLEXIBLE, null, null,
                List.of(YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)), null, null)),
                "FLIGHT_SEARCH_TOO_BROAD");
        assertCode(() -> service.plan(new FlightSearchRequest("BSB", List.of("MCZ", "MCZ"),
                FlightSearchRequest.Mode.EXACT, DEC_10, DEC_10, null, null, null)), "DUPLICATED_DESTINATION");
    }

    @Test
    void quotaPerPersonPerHour() {
        when(gateway.roundTrip(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        FlightSearchService service = service(true);
        service.search(exact("BSB", List.of("MCZ")));
        service.search(exact("BSB", List.of("MCZ")));
        assertCode(() -> service.search(exact("BSB", List.of("MCZ"))), "FLIGHT_SEARCH_QUOTA");
    }

    @Test
    void sameSearchInAnotherOrderIsTheSameHistoryEntry() throws Exception {
        FlightSearchRequest a = new FlightSearchRequest("BSB", List.of("REC", "MCZ"), FlightSearchRequest.Mode.FLEXIBLE,
                DEC_10, null, List.of(YearMonth.of(2027, 1), YearMonth.of(2026, 12)), null, 1);
        FlightSearchRequest b = new FlightSearchRequest("BSB", List.of("MCZ", "REC"), FlightSearchRequest.Mode.FLEXIBLE,
                null, null, List.of(YearMonth.of(2026, 12), YearMonth.of(2027, 1)), 5, null);
        assertThat(mapper.writeValueAsString(FlightSearchService.canonical(a)))
                .isEqualTo(mapper.writeValueAsString(FlightSearchService.canonical(b)));
        assertThat(FlightSearchService.summary(a)).isEqualTo("BSB → MCZ, REC · 12/2026, 01/2027 · 5 noites · 1 adulto");
    }

    private static FlightSearchRequest exact(String origin, List<String> destinations) {
        return new FlightSearchRequest(origin, destinations, FlightSearchRequest.Mode.EXACT, DEC_10,
                DEC_10.plusDays(5), null, null, null);
    }

    private static FlightOffer offer(LocalDate departure, String price) {
        return new FlightOffer(departure, departure.plusDays(5), new BigDecimal(price), "BRL", List.of("LATAM"), 0,
                180);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo(code));
    }
}
