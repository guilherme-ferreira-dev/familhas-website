package br.com.familhas.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.booking.BookingRepository;

/**
 * "Minhas viagens" ponta a ponta no Oracle real (V13 + seed), por HTTP com JWT: cada cliente só vê e só mexe no
 * que é dele (viagens, avaliações e favoritos), e a regra de viagem passada vale no banco (ADR 0010).
 */
@SpringBootTest(properties = { "familhas.outbox.publisher-enabled=false",
        "familhas.payments.reconciliation-enabled=false" })
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MinhasViagensIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    static final UUID PACKAGE_BUENOS_AIRES = UUID.fromString("bac00000-0000-4000-8000-000000000002");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final String ana = "kc-ana-" + UUID.randomUUID();
    private final String bruno = "kc-bruno-" + UUID.randomUUID();

    @Test
    void eachClientSeesOnlyTheirOwnTrips() throws Exception {
        UUID anaPast = confirm(book(ana), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12));
        UUID anaUpcoming = confirm(book(ana), null, null);
        UUID brunoTrip = confirm(book(bruno), null, null);
        UUID brunoPending = book(bruno);

        assertThat(tripIds(ana, "UPCOMING")).containsExactly(anaUpcoming);
        assertThat(tripIds(ana, "PAST")).containsExactly(anaPast);
        assertThat(tripIds(bruno, "UPCOMING")).containsExactly(brunoTrip);
        assertThat(tripIds(bruno, "PAST")).isEmpty();

        // Viagem de outro cliente e reserva que ainda não é viagem: 404, sem revelar existência.
        mockMvc.perform(as(ana, get("/api/me/trips/" + brunoTrip)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"));
        mockMvc.perform(as(bruno, get("/api/me/trips/" + anaPast)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(bruno, get("/api/me/trips/" + brunoPending)))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(ana, get("/api/me/trips/" + anaPast)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing").value("PAST"))
                .andExpect(jsonPath("$.canRate").value(true))
                .andExpect(jsonPath("$.travelPackage.id").value(PACKAGE_MACEIO.toString()))
                .andExpect(jsonPath("$.travelPackage.destinationCityName").value("Maceio"))
                .andExpect(jsonPath("$.items.length()").value(3));
    }

    @Test
    void ratingsFollowTheTripAndStayPrivate() throws Exception {
        confirm(book(ana), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12));
        confirm(book(bruno), null, null);

        UUID anaRating = UUID.fromString(json(rate(ana, PACKAGE_MACEIO, 5)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.travelPackage.id").value(PACKAGE_MACEIO.toString())))
                .get("id").asText());
        rate(ana, PACKAGE_MACEIO, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RATING_ALREADY_EXISTS"));

        // Bruno ainda não viajou: não pode avaliar. E não enxerga nem mexe na avaliação da Ana.
        rate(bruno, PACKAGE_MACEIO, 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RATING_NOT_ALLOWED"));
        mockMvc.perform(as(bruno, put("/api/me/ratings/" + anaRating)).contentType(MediaType.APPLICATION_JSON)
                        .content(ratingBody(PACKAGE_MACEIO, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATING_NOT_FOUND"));
        mockMvc.perform(as(bruno, delete("/api/me/ratings/" + anaRating)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(bruno, get("/api/me/ratings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(as(ana, get("/api/me/ratings")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(anaRating.toString()))
                .andExpect(jsonPath("$.content[0].rating").value(5));
        mockMvc.perform(as(ana, get("/api/me/trips").param("when", "PAST")))
                .andExpect(jsonPath("$.content[0].myRating.rating").value(5))
                .andExpect(jsonPath("$.content[0].canRate").value(false));
    }

    @Test
    void favoritesAreIdempotentAndPerClient() throws Exception {
        mockMvc.perform(as(ana, put("/api/me/favorites/" + PACKAGE_BUENOS_AIRES))).andExpect(status().isNoContent());
        mockMvc.perform(as(ana, put("/api/me/favorites/" + PACKAGE_BUENOS_AIRES))).andExpect(status().isNoContent());
        mockMvc.perform(as(ana, put("/api/me/favorites/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_FOUND"));

        mockMvc.perform(as(ana, get("/api/me/favorites")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(PACKAGE_BUENOS_AIRES.toString()))
                .andExpect(jsonPath("$[0].destinationCountry").value("Argentina"));
        mockMvc.perform(as(bruno, get("/api/me/favorites"))).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(as(ana, delete("/api/me/favorites/" + PACKAGE_BUENOS_AIRES)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(ana, delete("/api/me/favorites/" + PACKAGE_BUENOS_AIRES)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(ana, get("/api/me/favorites"))).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void recommendationsSkipWhatTheClientAlreadyBookedOrFavorited() throws Exception {
        book(ana);
        mockMvc.perform(as(ana, put("/api/me/favorites/" + PACKAGE_BUENOS_AIRES))).andExpect(status().isNoContent());

        List<String> recommended = new ArrayList<>();
        json(mockMvc.perform(as(ana, get("/api/me/recommendations").param("limit", "20")))
                .andExpect(status().isOk()))
                .forEach(r -> recommended.add(r.get("travelPackage").get("id").asText()));

        assertThat(recommended).doesNotContain(PACKAGE_MACEIO.toString(), PACKAGE_BUENOS_AIRES.toString());
    }

    private List<UUID> tripIds(String subject, String when) throws Exception {
        List<UUID> ids = new ArrayList<>();
        json(mockMvc.perform(as(subject, get("/api/me/trips").param("when", when))).andExpect(status().isOk()))
                .get("content").forEach(trip -> ids.add(UUID.fromString(trip.get("id").asText())));
        return ids;
    }

    private UUID book(String subject) throws Exception {
        JsonNode quote = json(mockMvc.perform(as(subject, post("/api/me/quotes")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"travelPackageId\":\"" + PACKAGE_MACEIO + "\",\"passengers\":1}"))
                .andExpect(status().isCreated()));
        JsonNode booking = json(mockMvc.perform(as(subject, post("/api/me/bookings"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"" + quote.get("id").asText() + "\"}"))
                .andExpect(status().isCreated()));
        return UUID.fromString(booking.get("id").asText());
    }

    /** Confirma como o webhook faria; com datas, leva todos os itens para esse período (viagem passada). */
    private UUID confirm(UUID bookingId, LocalDate start, LocalDate end) {
        transactionTemplate.executeWithoutResult(status -> {
            var booking = bookingRepository.lockById(bookingId).orElseThrow();
            assertThat(booking.confirm(Instant.now())).isTrue();
            if (start != null) {
                booking.getItems().forEach(item -> {
                    item.setStartDate(start);
                    item.setEndDate(end);
                });
            }
        });
        return bookingId;
    }

    private ResultActions rate(String subject, UUID travelPackageId, int rating) throws Exception {
        return mockMvc.perform(as(subject, post("/api/me/ratings")).contentType(MediaType.APPLICATION_JSON)
                .content(ratingBody(travelPackageId, rating)));
    }

    private static String ratingBody(UUID travelPackageId, int rating) {
        return "{\"rating\":" + rating + ",\"travelPackageId\":\"" + travelPackageId + "\"}";
    }

    private static MockHttpServletRequestBuilder as(String subject, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(subject).claim("email", subject + "@exemplo.com")
                        .claim("email_verified", true).claim("given_name", "Cliente").claim("name", "Cliente Teste"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
