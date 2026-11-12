package br.com.familhas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import br.com.familhas.carRental.CarRentalOfferRepository;
import br.com.familhas.category.CategoryRepository;
import br.com.familhas.city.CityEntity;
import br.com.familhas.city.CityRepository;
import br.com.familhas.city.CityService;
import br.com.familhas.city.dto.CityFilter;
import br.com.familhas.client.ClientService;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.client.KeycloakClientSyncService;
import br.com.familhas.client.dto.ClientFilter;
import br.com.familhas.common.PageResponse;
import br.com.familhas.enums.CityType;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.flight.FlightRepository;
import br.com.familhas.hotel.HotelRepository;
import br.com.familhas.hotel.RoomOfferRepository;
import br.com.familhas.rating.RatingRepository;
import br.com.familhas.travelPackage.TravelPackageRepository;
import br.com.familhas.travelPackage.TravelPackageService;
import br.com.familhas.travelPackage.dto.TravelPackageComponentRequest;
import br.com.familhas.travelPackage.dto.TravelPackageFilter;
import br.com.familhas.travelPackage.dto.TravelPackageRequest;
import br.com.familhas.travelPackage.dto.TravelPackageResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * Sobe um Oracle real, aplica as migrations V1..V8 via Flyway e valida o
 * mapeamento JPA (ddl-auto=validate no profile local). Pulado sem Docker.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // Sem RabbitMQ neste teste: o fluxo do outbox é coberto em OutboxMessagingIntegrationTests
        "familhas.outbox.publisher-enabled=false" })
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class OracleMigrationIntegrationTests {

    // Timeout padrao do OracleContainer e 60 s, curto para Docker Desktop recem-iniciado no Windows.
    @Container
    @ServiceConnection
    static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    private static final UUID PACKAGE_MACEIO = UUID.fromString("bac00000-0000-4000-8000-000000000001");
    private static final UUID PACKAGE_BUENOS_AIRES = UUID.fromString("bac00000-0000-4000-8000-000000000002");
    private static final UUID FLIGHT_GOL = UUID.fromString("f1170000-0000-4000-8000-000000000001");
    private static final UUID CATEGORY_PRAIA = UUID.fromString("ca7e0000-0000-4000-8000-000000000001");
    private static final UUID CITY_SAO_PAULO = UUID.fromString("c1770000-0000-4000-8000-000000000001");
    private static final UUID CITY_MACEIO = UUID.fromString("c1770000-0000-4000-8000-000000000002");
    private static final UUID ROOM_PAJUCARA = UUID.fromString("ee2e0000-0000-4000-8000-000000000001");
    private static final UUID CAR_LITORAL = UUID.fromString("ca2e0000-0000-4000-8000-000000000001");

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private FlightRepository flightRepository;
    @Autowired private TravelPackageRepository travelPackageRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private HotelRepository hotelRepository;
    @Autowired private RoomOfferRepository roomOfferRepository;
    @Autowired private CarRentalOfferRepository carRentalOfferRepository;
    @Autowired private TravelPackageService travelPackageService;
    @Autowired private CityService cityService;
    @Autowired private ClientService clientService;
    @Autowired private KeycloakClientSyncService keycloakClientSyncService;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void seedIsAppliedWithUuidIds() {
        assertThat(categoryRepository.count()).isEqualTo(3);
        assertThat(cityRepository.count()).isEqualTo(6);
        assertThat(flightRepository.count()).isEqualTo(3);
        assertThat(travelPackageRepository.count()).isEqualTo(2);
        assertThat(clientRepository.count()).isEqualTo(2);
        assertThat(ratingRepository.count()).isEqualTo(2);
        assertThat(hotelRepository.count()).isEqualTo(4);
        assertThat(roomOfferRepository.count()).isEqualTo(5);
        assertThat(carRentalOfferRepository.count()).isEqualTo(3);
    }

    @Test
    void seedUuidsMatchDocumentedValuesAndRelationships() {
        TravelPackageResponse response = travelPackageService.findById(PACKAGE_MACEIO);

        assertThat(response.title()).isEqualTo("Maceio Resort All Inclusive - 7 noites");
        assertThat(response.currency()).isEqualTo("BRL");
        assertThat(response.flightId()).isEqualTo(FLIGHT_GOL);
        assertThat(response.originCityId()).isEqualTo(CITY_SAO_PAULO);
        assertThat(response.destinationCityId()).isEqualTo(CITY_MACEIO);
        assertThat(response.components())
                .extracting(c -> c.type())
                .containsExactlyInAnyOrder(PackageComponentType.HOTEL_STAY, PackageComponentType.CAR_RENTAL);
    }

    @Test
    void newEntitiesReceiveGeneratedUuidAndAuditTimestamps() {
        CityEntity saved = cityRepository.saveAndFlush(CityEntity.builder()
                .name("Lisboa")
                .country("Portugal")
                .cityType(CityType.DESTINATION)
                .status(EntityStatus.ACTIVE)
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        entityManager.clear();
        assertThat(cityRepository.findById(CITY_MACEIO)).get()
                .satisfies(city -> assertThat(city.getCreatedAt()).isNotNull());
    }

    @Test
    void packageFiltersUseEffectivePriceDestinationAndText() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("title"));

        assertThat(ids(travelPackageService.findAll(
                new TravelPackageFilter(null, CITY_MACEIO, null, null, null, null, null), page)))
                .containsExactly(PACKAGE_MACEIO);
        // Maceio custa 2799.90 com promoção (3299.90 cheio); Buenos Aires 3099.00
        assertThat(ids(travelPackageService.findAll(
                new TravelPackageFilter(null, null, null, null, null, new BigDecimal("3000"), null), page)))
                .containsExactly(PACKAGE_MACEIO);
        assertThat(ids(travelPackageService.findAll(
                new TravelPackageFilter(null, null, null, null, null, null, "BUENOS"), page)))
                .containsExactly(PACKAGE_BUENOS_AIRES);
    }

    @Test
    void cityListIsPaginated() {
        PageResponse<?> first = cityService.findAll(CityFilter.empty(), PageRequest.of(0, 4, Sort.by("name")));

        assertThat(first.content()).hasSize(4);
        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    /**
     * Sem batch fetch seriam 1 + 2 por cliente (favoritos e viagens) = 5 queries.
     * Com default_batch_fetch_size: página + 1 lote de favoritos + 1 lote de viagens.
     */
    @Test
    void clientListLoadsCollectionsInBatchesWithoutNPlusOne() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        PageResponse<?> page = clientService.findAll(ClientFilter.empty(), PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.content()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    /** Página + 1 lote de componentes; voo, cidades e ofertas são lidos só pelo id (proxy). */
    @Test
    void packageListLoadsComponentsInOneBatch() {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();

        PageResponse<TravelPackageResponse> page = travelPackageService.findAll(
                TravelPackageFilter.empty(), PageRequest.of(0, 20, Sort.by("title")));

        assertThat(page.content()).hasSize(2);
        assertThat(page.content()).allSatisfy(p -> assertThat(p.components()).isNotEmpty());
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void deletingPackageWithRatingsIsBlockedByForeignKey() {
        assertThatThrownBy(() -> travelPackageService.delete(PACKAGE_MACEIO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void passwordColumnWasDroppedAndKeycloakIdIsUnique() {
        // V7: clients nao tem mais password e keycloak_id e UNIQUE (varios NULL permitidos).
        Number passwordColumns = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_tab_columns WHERE table_name = 'CLIENTS' AND column_name = 'PASSWORD'")
                .getSingleResult();
        Number keycloakColumns = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_tab_columns WHERE table_name = 'CLIENTS' AND column_name = 'KEYCLOAK_ID'")
                .getSingleResult();
        Number uniqueIndexes = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM user_constraints WHERE table_name = 'CLIENTS' "
                        + "AND constraint_type = 'U' AND constraint_name = 'UK_CLIENTS_KEYCLOAK_ID'")
                .getSingleResult();

        assertThat(passwordColumns.intValue()).isZero();
        assertThat(keycloakColumns.intValue()).isEqualTo(1);
        assertThat(uniqueIndexes.intValue()).isEqualTo(1);
    }

    /**
     * Primeiro login: vincula o Client local pelo e-mail verificado do Keycloak, sem duplicar.
     * O sync usa REQUIRES_NEW (commit próprio), por isso este teste não depende do rollback.
     */
    @Test
    void firstLoginLinksExistingClientByVerifiedEmail() {
        UUID first = keycloakClientSyncService.syncClientId(
                new KeycloakClientSyncService.KeycloakUser("sub-ana-teste", "ana.souza@familhas.com", true, "Ana", "Ana"));
        UUID second = keycloakClientSyncService.syncClientId(
                new KeycloakClientSyncService.KeycloakUser("sub-ana-teste", "ana.souza@familhas.com", true, "Ana", "Ana"));

        assertThat(first).isEqualTo(second);
        assertThat(clientRepository.findByKeycloakId("sub-ana-teste"))
                .get()
                .satisfies(client -> {
                    assertThat(client.getId()).isEqualTo(first);
                    assertThat(client.getEmail()).isEqualTo("ana.souza@familhas.com");
                });
    }

    @Test
    void deletingPackageWithoutDependentsRemovesItsComponents() {
        TravelPackageResponse created = travelPackageService.create(new TravelPackageRequest(
                "Pacote temporario", new BigDecimal("1000.00"), null, null, EntityStatus.ACTIVE, null,
                FLIGHT_GOL, CATEGORY_PRAIA, CITY_SAO_PAULO, CITY_MACEIO,
                List.of(new TravelPackageComponentRequest(PackageComponentType.HOTEL_STAY, ROOM_PAJUCARA, null, 2),
                        new TravelPackageComponentRequest(PackageComponentType.CAR_RENTAL, null, CAR_LITORAL, 2))));
        entityManager.flush();
        assertThat(created.components()).hasSize(2);
        assertThat(created.currency()).isEqualTo("BRL");

        travelPackageService.delete(created.id());

        assertThat(travelPackageRepository.findById(created.id())).isEmpty();
        Number remaining = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM travel_package_components WHERE travel_package_id = HEXTORAW(:id)")
                .setParameter("id", created.id().toString().replace("-", ""))
                .getSingleResult();
        assertThat(remaining.intValue()).isZero();
    }

    private static List<UUID> ids(PageResponse<TravelPackageResponse> page) {
        return page.content().stream().map(TravelPackageResponse::id).toList();
    }
}
