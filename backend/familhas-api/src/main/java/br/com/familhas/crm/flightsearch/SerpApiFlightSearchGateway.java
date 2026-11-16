package br.com.familhas.crm.flightsearch;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import br.com.familhas.api.BusinessException;

/**
 * SerpApi, engine google_flights, ida e volta (type=1) em BRL. A chave fica só no servidor e nunca vai para log:
 * os erros registram trecho e datas, não a URL. Resultado em cache por trecho, datas e adultos.
 */
public class SerpApiFlightSearchGateway implements FlightSearchGateway {

    private static final Logger log = LoggerFactory.getLogger(SerpApiFlightSearchGateway.class);
    static final String NO_RESULTS = "hasn't returned any results";

    private final RestClient client;
    private final String apiKey;
    private final Cache<String, List<FlightOffer>> cache;

    public SerpApiFlightSearchGateway(RestClient client, String apiKey, FlightSearchProperties properties) {
        this.client = client;
        this.apiKey = apiKey;
        this.cache = Caffeine.newBuilder().expireAfterWrite(properties.cacheTtl()).maximumSize(2_000).build();
    }

    @Override
    public List<FlightOffer> roundTrip(String origin, String destination, LocalDate departure, LocalDate returnDate,
            int adults) {
        String key = origin + destination + departure + returnDate + adults;
        return cache.get(key, k -> fetch(origin, destination, departure, returnDate, adults));
    }

    private List<FlightOffer> fetch(String origin, String destination, LocalDate departure, LocalDate returnDate,
            int adults) {
        JsonNode body;
        try {
            body = client.get().uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_flights")
                            .queryParam("type", "1")
                            .queryParam("departure_id", origin)
                            .queryParam("arrival_id", destination)
                            .queryParam("outbound_date", departure)
                            .queryParam("return_date", returnDate)
                            .queryParam("adults", adults)
                            .queryParam("currency", "BRL")
                            .queryParam("hl", "pt-br")
                            .queryParam("gl", "br")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.value() == 400, (request, response) -> {
                        // 400 da SerpApi com "sem resultados" é resposta normal; o corpo é lido abaixo.
                    })
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("SerpApi falhou: {}->{} {} {} ({})", origin, destination, departure, returnDate,
                    e.getClass().getSimpleName());
            throw unavailable();
        }
        return parse(body, departure, returnDate, origin, destination);
    }

    static List<FlightOffer> parse(JsonNode body, LocalDate departure, LocalDate returnDate, String origin,
            String destination) {
        if (body == null) {
            throw unavailable();
        }
        if (body.hasNonNull("error")) {
            if (body.get("error").asText().contains(NO_RESULTS)) {
                return List.of();
            }
            log.warn("SerpApi recusou a pesquisa {}->{} {}: {}", origin, destination, departure,
                    body.get("error").asText());
            throw unavailable();
        }
        List<FlightOffer> offers = new ArrayList<>();
        for (String section : List.of("best_flights", "other_flights")) {
            for (JsonNode option : body.path(section)) {
                if (!option.path("price").isNumber()) {
                    continue;
                }
                Set<String> airlines = new LinkedHashSet<>();
                option.path("flights").forEach(leg -> {
                    if (leg.hasNonNull("airline")) {
                        airlines.add(leg.get("airline").asText());
                    }
                });
                int legs = option.path("flights").size();
                Integer duration = option.path("total_duration").isNumber() ? option.get("total_duration").asInt()
                        : null;
                offers.add(new FlightOffer(departure, returnDate, option.get("price").decimalValue()
                        .setScale(2, java.math.RoundingMode.HALF_EVEN), "BRL", List.copyOf(airlines),
                        Math.max(0, legs - 1), duration));
            }
        }
        offers.sort(java.util.Comparator.comparing(FlightOffer::price));
        return offers;
    }

    static BusinessException unavailable() {
        return new BusinessException(HttpStatus.BAD_GATEWAY, "FLIGHT_SEARCH_FAILED",
                "A pesquisa de voos nao respondeu. Tente de novo em instantes.");
    }

}
