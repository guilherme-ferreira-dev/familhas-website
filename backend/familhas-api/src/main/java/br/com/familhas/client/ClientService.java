package br.com.familhas.client;

import br.com.familhas.client.dto.ClientFilter;
import br.com.familhas.client.dto.ClientRequest;
import br.com.familhas.client.dto.ClientResponse;
import br.com.familhas.client.dto.ClientSegmentCounts;
import br.com.familhas.client.dto.MeUpdateRequest;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.booking.BookingEntity;
import br.com.familhas.booking.BookingStatus;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.travelPackage.TravelPackageEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@Transactional
public class ClientService {

    /** Lista branca: password nunca é ordenável nem filtrável. */
    static final Set<String> SORTABLE = Set.of("name", "email", "createdAt", "updatedAt");

    private final ClientRepository clientRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public ClientService(ClientRepository clientRepository, EntityManager entityManager, Clock clock) {
        this.clientRepository = clientRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * favorites/myFlights são LAZY e carregados em lote (default_batch_fetch_size),
     * então a página custa poucas queries em vez de 1 + 2 por cliente.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> findAll(ClientFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(clientRepository.findAll(toSpecification(filter), pageable), this::toResponse);
    }

    /** Quantos clientes há em cada aba do Admin (uma contagem por aba). */
    @Transactional(readOnly = true)
    public ClientSegmentCounts countSegments() {
        Instant cutoff = newClientCutoff();
        return new ClientSegmentCounts(
                clientRepository.count(),
                clientRepository.count(segment(ClientSegment.NEW, cutoff)),
                clientRepository.count(segment(ClientSegment.ACTIVE, cutoff)),
                clientRepository.count(segment(ClientSegment.PAST, cutoff)));
    }

    private Instant newClientCutoff() {
        return clock.instant().minus(Duration.ofDays(ClientSegment.NEW_CLIENT_DAYS));
    }

    /** Regras de cada aba em {@link ClientSegment}. Subconsultas EXISTS em bookings, sem join na lista. */
    static Specification<ClientEntity> segment(ClientSegment segment, Instant newClientCutoff) {
        return (root, query, cb) -> {
            Predicate active = hasBooking(root, query, cb, BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED);
            Predicate isNew = cb.and(
                    cb.not(active),
                    cb.equal(root.get("status"), EntityStatus.ACTIVE),
                    cb.greaterThanOrEqualTo(root.get("createdAt"), newClientCutoff),
                    cb.not(hasBooking(root, query, cb, BookingStatus.COMPLETED)));
            return switch (segment) {
                case ACTIVE -> active;
                case NEW -> isNew;
                case PAST -> cb.and(
                        cb.not(active),
                        cb.not(isNew),
                        cb.or(hasBooking(root, query, cb, BookingStatus.COMPLETED, BookingStatus.CANCELLED),
                                cb.equal(root.get("status"), EntityStatus.INACTIVE)));
            };
        };
    }

    private static Predicate hasBooking(Root<ClientEntity> client, CriteriaQuery<?> query, CriteriaBuilder cb,
            BookingStatus... statuses) {
        Subquery<Integer> booking = query.subquery(Integer.class);
        Root<BookingEntity> b = booking.from(BookingEntity.class);
        booking.select(cb.literal(1)).where(cb.equal(b.get("client"), client), b.get("status").in((Object[]) statuses));
        return cb.exists(booking);
    }

    private Specification<ClientEntity> toSpecification(ClientFilter filter) {
        List<Specification<ClientEntity>> specs = new ArrayList<>();
        if (filter.status() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (Specs.hasText(filter.q())) {
            String pattern = Specs.likePattern(filter.q());
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("name")), pattern, Specs.escapeChar()),
                    cb.like(cb.lower(root.<String>get("email")), pattern, Specs.escapeChar())));
        }
        if (filter.segment() != null) {
            specs.add(segment(filter.segment(), newClientCutoff()));
        }
        return Specification.allOf(specs);
    }

    @Transactional(readOnly = true)
    public ClientResponse findById(UUID id) {
        return toResponse(getClientOrThrow(id));
    }

    public ClientResponse create(ClientRequest request) {
        validateEmailUnique(request.email(), null);
        ClientEntity client = new ClientEntity();
        apply(client, request);
        return toResponse(clientRepository.save(client));
    }

    public ClientResponse update(UUID id, ClientRequest request) {
        validateEmailUnique(request.email(), id);
        ClientEntity client = getClientOrThrow(id);
        apply(client, request);
        return toResponse(clientRepository.save(client));
    }

    public void delete(UUID id) {
        ClientEntity client = getClientOrThrow(id);
        clientRepository.delete(client);
        clientRepository.flush();
    }

    /** Atualização do próprio perfil: só nome e telefone. E-mail e status não passam por aqui. */
    public void updateProfile(UUID clientId, MeUpdateRequest request) {
        ClientEntity client = getClientOrThrow(clientId);
        client.setName(request.name().trim());
        client.setPhoneNumber(blankToNull(request.phoneNumber()));
    }

    private void validateEmailUnique(String email, UUID excludeClientId) {
        String normalized = email.trim().toLowerCase();
        boolean conflict;
        if (excludeClientId == null) {
            conflict = clientRepository.existsByEmailIgnoreCase(normalized);
        } else {
            conflict = clientRepository.existsByEmailIgnoreCaseAndIdNot(normalized, excludeClientId);
        }
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
    }

    private ClientEntity getClientOrThrow(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id));
    }

    private TravelPackageEntity getTravelPackageOrThrow(UUID id) {
        TravelPackageEntity entity = entityManager.find(TravelPackageEntity.class, id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Travel package not found: " + id);
        }
        return entity;
    }

    private List<TravelPackageEntity> mapTravelPackages(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<TravelPackageEntity> mapped = new ArrayList<>();
        for (UUID id : ids) {
            mapped.add(getTravelPackageOrThrow(id));
        }
        return mapped;
    }

    private void apply(ClientEntity client, ClientRequest request) {
        client.setName(request.name().trim());
        setEmail(client, request.email());
        client.setPhoneNumber(blankToNull(request.phoneNumber()));
        client.setStatus(request.status());
        if (request.favoriteIds() != null) {
            client.getFavorites().clear();
            client.getFavorites().addAll(mapTravelPackages(request.favoriteIds()));
        }
        if (request.myFlightIds() != null) {
            client.getMyFlights().clear();
            client.getMyFlights().addAll(mapTravelPackages(request.myFlightIds()));
        }
    }

    /**
     * O e-mail é a chave do vínculo com o Keycloak. Depois do primeiro login, só o Keycloak
     * pode alterá-lo: mudar aqui criaria uma conta que não corresponde ao usuário autenticado.
     */
    void setEmail(ClientEntity client, String email) {
        String normalized = email.trim().toLowerCase();
        if (client.getKeycloakId() != null) {
            if (!normalized.equalsIgnoreCase(client.getEmail())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Email is managed by Keycloak for accounts with login; change it there");
            }
            return;
        }
        client.setEmail(normalized);
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private ClientResponse toResponse(ClientEntity client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getEmail(),
                client.getPhoneNumber(),
                client.getStatus(),
                client.getKeycloakId() != null,
                client.getFavorites().stream().map(TravelPackageEntity::getId).toList(),
                client.getMyFlights().stream().map(TravelPackageEntity::getId).toList());
    }
}
