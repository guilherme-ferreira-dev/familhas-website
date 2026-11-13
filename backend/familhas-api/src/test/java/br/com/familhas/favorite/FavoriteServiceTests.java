package br.com.familhas.favorite;

import static br.com.familhas.trip.TripFixtures.category;
import static br.com.familhas.trip.TripFixtures.city;
import static br.com.familhas.trip.TripFixtures.client;
import static br.com.familhas.trip.TripFixtures.travelPackage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.familhas.api.BusinessException;
import br.com.familhas.client.ClientEntity;
import br.com.familhas.client.ClientRepository;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.travelPackage.TravelPackageEntity;
import br.com.familhas.travelPackage.TravelPackageRepository;
import br.com.familhas.travelPackage.dto.PackageSummaryResponse;

class FavoriteServiceTests {

    private final ClientRepository clients = mock(ClientRepository.class);
    private final TravelPackageRepository packages = mock(TravelPackageRepository.class);
    private final FavoriteService service = new FavoriteService(clients, packages);

    private ClientEntity ana;
    private TravelPackageEntity maceio;
    private TravelPackageEntity buenosAires;

    @BeforeEach
    void setUp() {
        ana = client();
        maceio = travelPackage("Maceio 7 noites", category("Praia"), city("Maceio", "Brasil"),
                LocalDateTime.of(2027, 3, 10, 6, 20));
        buenosAires = travelPackage("Buenos Aires classica", category("Cidade"), city("Buenos Aires", "Argentina"),
                LocalDateTime.of(2027, 4, 3, 11, 10));
        when(clients.findById(ana.getId())).thenReturn(Optional.of(ana));
        when(packages.findById(maceio.getId())).thenReturn(Optional.of(maceio));
        when(packages.findById(buenosAires.getId())).thenReturn(Optional.of(buenosAires));
    }

    @Test
    void addIsIdempotentAndListIsSortedByTitle() {
        service.add(ana.getId(), maceio.getId());
        service.add(ana.getId(), maceio.getId());
        service.add(ana.getId(), buenosAires.getId());

        assertThat(service.findForClient(ana.getId())).extracting(PackageSummaryResponse::title)
                .containsExactly("Buenos Aires classica", "Maceio 7 noites");
    }

    @Test
    void removeIsIdempotent() {
        ana.getFavorites().add(maceio);

        service.remove(ana.getId(), maceio.getId());
        service.remove(ana.getId(), maceio.getId());

        assertThat(ana.getFavorites()).isEmpty();
    }

    @Test
    void inactiveOrUnknownPackageIs404WithCode() {
        maceio.setStatus(EntityStatus.INACTIVE);
        UUID unknown = UUID.randomUUID();
        when(packages.findById(unknown)).thenReturn(Optional.empty());

        for (UUID id : new UUID[] { maceio.getId(), unknown }) {
            assertThatThrownBy(() -> service.add(ana.getId(), id))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.code()).isEqualTo("PACKAGE_NOT_FOUND"));
        }
        assertThat(ana.getFavorites()).isEmpty();
    }

    @Test
    void alreadyFavoriteDoesNotLookUpThePackageAgain() {
        ana.getFavorites().add(maceio);
        maceio.setStatus(EntityStatus.INACTIVE);

        service.add(ana.getId(), maceio.getId());

        verify(packages, never()).findById(any());
        assertThat(service.findForClient(ana.getId())).singleElement()
                .satisfies(p -> assertThat(p.active()).isFalse());
    }
}
