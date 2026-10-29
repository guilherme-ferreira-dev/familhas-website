package br.com.familhas.client;

import br.com.familhas.client.dto.ClientFilter;
import br.com.familhas.client.dto.ClientRequest;
import br.com.familhas.client.dto.ClientResponse;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.travelPackage.TravelPackageEntity;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    public ClientService(ClientRepository clientRepository, EntityManager entityManager) {
        this.clientRepository = clientRepository;
        this.entityManager = entityManager;
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

    private static Specification<ClientEntity> toSpecification(ClientFilter filter) {
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
        client.setEmail(request.email().trim().toLowerCase());
        client.setPhoneNumber(blankToNull(request.phoneNumber()));
        client.setPassword(request.password());
        client.setStatus(request.status());
        client.setFavorites(mapTravelPackages(request.favoriteIds()));
        client.setMyFlights(mapTravelPackages(request.myFlightIds()));
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
                client.getFavorites().stream().map(TravelPackageEntity::getId).toList(),
                client.getMyFlights().stream().map(TravelPackageEntity::getId).toList());
    }
}
