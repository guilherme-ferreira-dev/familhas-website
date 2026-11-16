package br.com.familhas.crm.flightsearch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationRegistry;

/**
 * Pesquisar Voos (ADR 0015). Cota cada destino separado, em ida e volta. Modo flexível: testa datas de ida a cada
 * {@code flexibleStepDays} dias nos meses escolhidos, com a estadia pedida, e devolve o menor preço de cada data.
 * Custo controlado: teto de chamadas por pesquisa e de pesquisas por pessoa por hora.
 */
@Service
public class FlightSearchService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    static final int MAX_PARALLEL_CALLS = 4;

    private final FlightSearchGateway gateway;
    private final IntegrationRegistry integrations;
    private final FlightSearchProperties properties;
    private final FlightSearchRepository history;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Cache<String, AtomicInteger> quota;

    public FlightSearchService(FlightSearchGateway gateway, IntegrationRegistry integrations,
            FlightSearchProperties properties, FlightSearchRepository history, ObjectMapper objectMapper,
            Clock clock) {
        this.gateway = gateway;
        this.integrations = integrations;
        this.properties = properties;
        this.history = history;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.quota = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).build();
    }

    public FlightSearchOptionsResponse options() {
        return new FlightSearchOptionsResponse(properties.origins(),
                integrations.isConfigured(CrmIntegration.FLIGHT_SEARCH), 5, 7, 3);
    }

    public FlightSearchResponse search(FlightSearchRequest request) {
        List<Trip> trips = plan(request);
        integrations.require(CrmIntegration.FLIGHT_SEARCH);
        takeQuota();

        List<FlightSearchResponse.DestinationResult> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore slots = new Semaphore(MAX_PARALLEL_CALLS);
            for (String destination : request.destinations()) {
                List<Future<List<FlightOffer>>> calls = trips.stream().map(trip -> executor.submit(() -> {
                    slots.acquire();
                    try {
                        return gateway.roundTrip(request.origin(), destination, trip.departure(), trip.ret(),
                                request.adultCount());
                    } finally {
                        slots.release();
                    }
                })).toList();
                results.add(collect(destination, calls, request.mode()));
            }
        }
        record(request);
        return new FlightSearchResponse(request, results);
    }

    @Transactional(readOnly = true)
    public List<RecentSearchResponse> recent() {
        return history.findTop5ByOrderByLastRunAtDesc().stream().map(entity -> {
            try {
                return new RecentSearchResponse(entity.getId(), entity.getSummary(),
                        objectMapper.readValue(entity.getParams(), FlightSearchRequest.class), entity.getRunBy(),
                        entity.getLastRunAt());
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Pesquisa salva ilegivel: " + entity.getId(), e);
            }
        }).toList();
    }

    /** Datas a consultar, depois de validar a combinação. Recusa pesquisa cara demais antes de gastar chamadas. */
    List<Trip> plan(FlightSearchRequest request) {
        if (!properties.origins().contains(request.origin())) {
            throw invalid("ORIGIN_NOT_ALLOWED", "Origem fora da lista configurada.");
        }
        if (request.destinations().contains(request.origin())) {
            throw invalid("SAME_ORIGIN_DESTINATION", "Destino igual a origem.");
        }
        if (request.destinations().stream().distinct().count() != request.destinations().size()) {
            throw invalid("DUPLICATED_DESTINATION", "Destino repetido.");
        }
        LocalDate today = BusinessTime.today(clock);
        List<Trip> trips = new ArrayList<>();
        if (request.mode() == FlightSearchRequest.Mode.EXACT) {
            if (request.departureDate() == null || request.returnDate() == null) {
                throw invalid("DATES_REQUIRED", "Informe ida e volta.");
            }
            if (request.departureDate().isBefore(today) || request.returnDate().isBefore(request.departureDate())) {
                throw invalid("INVALID_DATES", "Ida a partir de hoje e volta depois da ida.");
            }
            trips.add(new Trip(request.departureDate(), request.returnDate()));
        } else {
            if (request.months() == null || request.months().isEmpty()) {
                throw invalid("MONTHS_REQUIRED", "Escolha de 1 a 3 meses.");
            }
            for (YearMonth month : request.months().stream().distinct().sorted().toList()) {
                if (month.isBefore(YearMonth.from(today))) {
                    throw invalid("INVALID_DATES", "Mes ja passou: " + month.format(MONTH));
                }
                for (int day = 1; day <= month.lengthOfMonth(); day += properties.flexibleStepDays()) {
                    LocalDate departure = month.atDay(day);
                    if (departure.isAfter(today)) {
                        trips.add(new Trip(departure, departure.plusDays(request.stay())));
                    }
                }
            }
            if (trips.isEmpty()) {
                throw invalid("INVALID_DATES", "Nenhuma data futura nos meses escolhidos.");
            }
        }
        int calls = trips.size() * request.destinations().size();
        if (calls > properties.maxCallsPerSearch()) {
            throw invalid("FLIGHT_SEARCH_TOO_BROAD", "Pesquisa grande demais (" + calls + " consultas; maximo "
                    + properties.maxCallsPerSearch() + "). Escolha menos destinos ou meses.");
        }
        return trips;
    }

    private FlightSearchResponse.DestinationResult collect(String destination, List<Future<List<FlightOffer>>> calls,
            FlightSearchRequest.Mode mode) {
        List<FlightOffer> offers = new ArrayList<>();
        int failures = 0;
        for (Future<List<FlightOffer>> call : calls) {
            try {
                List<FlightOffer> found = call.get();
                if (mode == FlightSearchRequest.Mode.FLEXIBLE) {
                    // Flexível: o menor preço de cada data testada.
                    found.stream().min(Comparator.comparing(FlightOffer::price)).ifPresent(offers::add);
                } else {
                    offers.addAll(found.stream().limit(10).toList());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures++;
            } catch (ExecutionException e) {
                failures++;
            }
        }
        offers.sort(Comparator.comparing(FlightOffer::price));
        String error = failures == 0 ? null
                : failures == calls.size() ? "A pesquisa deste destino falhou." : "Algumas datas nao responderam.";
        return new FlightSearchResponse.DestinationResult(destination, offers, error);
    }

    private void takeQuota() {
        String who = AuditActor.current().subject();
        int used = quota.get(who, k -> new AtomicInteger()).incrementAndGet();
        if (used > properties.searchesPerHour()) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "FLIGHT_SEARCH_QUOTA",
                    "Limite de " + properties.searchesPerHour() + " pesquisas por hora atingido.");
        }
    }

    /** Guarda a pesquisa; a mesma de novo só renova o horário. Duas iguais ao mesmo tempo: vale a que gravou. */
    void record(FlightSearchRequest request) {
        String params;
        try {
            params = objectMapper.writeValueAsString(canonical(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String hash = sha256(params);
        FlightSearchEntity entity = history.findByParamsHash(hash).orElseGet(() -> {
            FlightSearchEntity created = new FlightSearchEntity();
            created.setParamsHash(hash);
            created.setParams(params);
            return created;
        });
        entity.setSummary(summary(request));
        entity.setRunBy(AuditActor.current().displayName());
        entity.setLastRunAt(clock.instant());
        try {
            history.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Mesma pesquisa gravada por outra requisição neste instante: o histórico já está certo.
        }
    }

    /** Mesma pesquisa = mesmos parâmetros, sem importar ordem de destinos e meses. */
    static FlightSearchRequest canonical(FlightSearchRequest r) {
        boolean flexible = r.mode() == FlightSearchRequest.Mode.FLEXIBLE;
        return new FlightSearchRequest(r.origin(), r.destinations().stream().sorted().toList(), r.mode(),
                flexible ? null : r.departureDate(), flexible ? null : r.returnDate(),
                flexible ? r.months().stream().distinct().sorted().toList() : null, flexible ? r.stay() : null,
                r.adultCount());
    }

    static String summary(FlightSearchRequest r) {
        String route = r.origin() + " → " + String.join(", ", r.destinations().stream().sorted().toList());
        String when = r.mode() == FlightSearchRequest.Mode.EXACT
                ? r.departureDate().format(DAY) + " a " + r.returnDate().format(DAY)
                : r.months().stream().distinct().sorted().map(m -> m.format(MONTH)).collect(Collectors.joining(", "))
                        + " · " + r.stay() + " noites";
        return route + " · " + when + " · " + (r.adultCount() == 1 ? "1 adulto" : r.adultCount() + " adultos");
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    record Trip(LocalDate departure, LocalDate ret) {
    }
}
