package br.com.familhas.crm.flightsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import br.com.familhas.api.BusinessException;

/** Contrato com a SerpApi (engine google_flights) sem rede: MockRestServiceServer. */
class SerpApiFlightSearchGatewayTests {

    private static final LocalDate OUT = LocalDate.of(2026, 12, 10);
    private static final LocalDate BACK = LocalDate.of(2026, 12, 15);
    private static final String BODY = """
            {"best_flights":[{"flights":[{"airline":"LATAM"},{"airline":"GOL"}],"total_duration":310,"price":1890},
              {"flights":[{"airline":"Azul"}],"total_duration":150,"price":1650}],
             "other_flights":[{"flights":[{"airline":"GOL"}],"price":2200},{"flights":[],"price":"indisponivel"}]}
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://serpapi.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SerpApiFlightSearchGateway gateway = new SerpApiFlightSearchGateway(builder.build(), "segredo",
            new FlightSearchProperties(List.of("BSB"), "", Duration.ofSeconds(5), 40, 7, 30, Duration.ofMinutes(30)));

    @Test
    void sendsTheRoundTripQueryAndParsesCheapestFirstWithCache() {
        server.expect(once(), requestTo(Matchers.startsWith("https://serpapi.test/search.json")))
                .andExpect(queryParam("engine", "google_flights"))
                .andExpect(queryParam("type", "1"))
                .andExpect(queryParam("departure_id", "BSB"))
                .andExpect(queryParam("arrival_id", "MCZ"))
                .andExpect(queryParam("outbound_date", "2026-12-10"))
                .andExpect(queryParam("return_date", "2026-12-15"))
                .andExpect(queryParam("currency", "BRL"))
                .andExpect(queryParam("api_key", "segredo"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<FlightOffer> offers = gateway.roundTrip("BSB", "MCZ", OUT, BACK, 2);
        assertThat(offers).extracting(o -> o.price().toPlainString()).containsExactly("1650.00", "1890.00", "2200.00");
        assertThat(offers.get(1).airlines()).containsExactly("LATAM", "GOL");
        assertThat(offers.get(1).stops()).isEqualTo(1);
        assertThat(offers.get(0).durationMinutes()).isEqualTo(150);

        // Mesmo trecho e datas: vem do cache, sem segunda chamada (o servidor aceita uma só).
        assertThat(gateway.roundTrip("BSB", "MCZ", OUT, BACK, 2)).hasSize(3);
        server.verify();
    }

    @Test
    void noResultsIsAnEmptyListAndOtherErrorsAre502() {
        server.expect(requestTo(Matchers.containsString("arrival_id=FEN")))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Google Flights hasn't returned any results for this query.\"}"));
        server.expect(requestTo(Matchers.containsString("arrival_id=REC")))
                .andRespond(withSuccess("{\"error\":\"Invalid API key.\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("arrival_id=SSA"))).andRespond(withServerError());

        assertThat(gateway.roundTrip("BSB", "FEN", OUT, BACK, 1)).isEmpty();
        assertThatThrownBy(() -> gateway.roundTrip("BSB", "REC", OUT, BACK, 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("FLIGHT_SEARCH_FAILED"));
        assertThatThrownBy(() -> gateway.roundTrip("BSB", "SSA", OUT, BACK, 1))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("FLIGHT_SEARCH_FAILED"))
                .hasMessageNotContaining("segredo");
        server.verify();
    }
}
