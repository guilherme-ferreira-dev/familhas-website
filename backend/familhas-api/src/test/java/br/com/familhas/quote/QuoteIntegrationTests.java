package br.com.familhas.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.provider.flight.CuratedFlightProvider;
import br.com.familhas.provider.hotel.CuratedHotelProvider;
import br.com.familhas.quote.dto.QuoteRequest;
import br.com.familhas.quote.dto.QuoteRequest.FlightSelection;
import br.com.familhas.quote.dto.QuoteRequest.HotelSelection;
import br.com.familhas.quote.dto.QuoteResponse;

/** Cotação ponta a ponta no Oracle real (V10 + seed): pacote, itens avulsos, moedas e isolamento por cliente. */
@SpringBootTest(properties = "familhas.outbox.publisher-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class QuoteIntegrationTests {

    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final UUID ANA = UUID.fromString("c11e0000-0000-4000-8000-000000000001");
    static final UUID BRUNO = UUID.fromString("c11e0000-0000-4000-8000-000000000002");
    static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");
    static final UUID ROOM_PAJUCARA = UUID.fromString("ee2e0000-0000-4000-8000-000000000001");
    static final UUID ROOM_ORLANDO_USD = UUID.fromString("ee2e0000-0000-4000-8000-000000000005");
    static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);

    @Autowired private QuoteService quoteService;

    @Test
    void packageQuoteIsPersistedWithItsSnapshotAndReadBackByTheOwner() {
        QuoteResponse created = quoteService.create(ANA, new QuoteRequest(PACKAGE_MACEIO, 2, null, null, null));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(QuoteStatus.OPEN);
        assertThat(created.total()).isEqualByComparingTo("7830.16");
        assertThat(created.discount()).isEqualByComparingTo("1398.29");
        assertThat(created.travelPackageId()).isEqualTo(PACKAGE_MACEIO);

        QuoteResponse read = quoteService.findForClient(ANA, created.id());
        assertThat(read.items()).hasSize(3);
        assertThat(read.items().get(0).startDate()).isEqualTo(DEPARTURE);
        assertThat(read.total()).isEqualByComparingTo(created.total());
        assertThat(quoteService.findAllForClient(ANA, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))
                .content()).extracting(q -> q.id()).contains(created.id());
    }

    @Test
    void anotherClientCannotSeeTheQuote() {
        QuoteResponse created = quoteService.create(ANA, new QuoteRequest(null, 1,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_GOL)), null, null));

        assertThatThrownBy(() -> quoteService.findForClient(BRUNO, created.id()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void separateItemsWithHotelInDollarsAndFlightInReaisAreRejected() {
        assertThatThrownBy(() -> quoteService.create(ANA, new QuoteRequest(null, 2,
                new FlightSelection(CuratedFlightProvider.offerIdOf(FLIGHT_GOL)),
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_ORLANDO_USD), DEPARTURE, DEPARTURE.plusDays(3)),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void separateHotelQuoteChargesRoomsTimesNights() {
        QuoteResponse created = quoteService.create(BRUNO, new QuoteRequest(null, 3, null,
                new HotelSelection(CuratedHotelProvider.offerIdOf(ROOM_PAJUCARA), DEPARTURE, DEPARTURE.plusDays(2)),
                null));

        // 3 hóspedes em quarto duplo = 2 quartos x 2 noites x 890 = 3560 + 10% = 3916
        assertThat(created.items().get(0).quantity()).isEqualTo(4);
        assertThat(created.total()).isEqualByComparingTo("3916.00");
    }
}
