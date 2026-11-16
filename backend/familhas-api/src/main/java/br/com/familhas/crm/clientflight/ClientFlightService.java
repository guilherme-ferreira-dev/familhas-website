package br.com.familhas.crm.clientflight;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.crm.common.Cpf;
import br.com.familhas.crm.common.SearchText;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Voos Clientes (JSON: voo_flights e voo_travelers). Lista pela data da ida, busca rápida por passageiro sem acento
 * e em qualquer ordem, arquivados e anexos (ADR 0015). Permissões conferidas no controller (@PreAuthorize).
 */
@Service
public class ClientFlightService {

    static final Set<String> SORTABLE = Set.of("outboundAt", "airline", "destination", "createdAt");

    private final ClientFlightRepository repository;
    private final CrmFileService files;

    public ClientFlightService(ClientFlightRepository repository, CrmFileService files) {
        this.repository = repository;
        this.files = files;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientFlightSummary> search(ClientFlightFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        // Ativos: o próximo voo primeiro. Arquivados (histórico): o mais recente primeiro.
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort()
                : Sort.by(filter.archived() ? Sort.Direction.DESC : Sort.Direction.ASC, "outboundAt");
        Page<ClientFlightEntity> page = repository.findAll(specification(filter),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id"))));
        return PageResponse.of(page, ClientFlightService::summary);
    }

    @Transactional(readOnly = true)
    public ClientFlightResponse get(UUID id) {
        return response(find(id));
    }

    @Transactional
    public ClientFlightResponse create(ClientFlightRequest request) {
        ClientFlightEntity flight = new ClientFlightEntity();
        flight.setCreatedBy(AuditActor.current().displayName());
        apply(flight, request);
        return response(repository.saveAndFlush(flight));
    }

    @Transactional
    public ClientFlightResponse update(UUID id, ClientFlightRequest request) {
        ClientFlightEntity flight = find(id);
        checkVersion(flight, request.version());
        apply(flight, request);
        return response(repository.saveAndFlush(flight));
    }

    @Transactional
    public ClientFlightResponse archive(UUID id, boolean archived) {
        ClientFlightEntity flight = find(id);
        flight.setArchivedAt(archived ? (flight.getArchivedAt() != null ? flight.getArchivedAt() : Instant.now()) : null);
        return response(repository.saveAndFlush(flight));
    }

    /** Exclui o voo e os arquivos anexados (do banco e do storage). */
    @Transactional
    public void delete(UUID id) {
        ClientFlightEntity flight = find(id);
        List<UUID> attachments = new ArrayList<>(flight.getAttachmentIds());
        repository.delete(flight);
        repository.flush();
        attachments.forEach(files::delete);
    }

    @Transactional
    public ClientFlightResponse attach(UUID id, MultipartFile file) {
        ClientFlightEntity flight = find(id);
        if (flight.getAttachmentIds().size() >= 20) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TOO_MANY_ATTACHMENTS",
                    "Limite de 20 anexos por voo.");
        }
        CrmFileResponse stored = files.upload(FileCategory.FLIGHT_ATTACHMENT, file);
        flight.getAttachmentIds().add(stored.id());
        return response(repository.saveAndFlush(flight));
    }

    @Transactional
    public ClientFlightResponse detach(UUID id, UUID fileId) {
        ClientFlightEntity flight = find(id);
        if (!flight.getAttachmentIds().remove(fileId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "Anexo nao encontrado neste voo.");
        }
        repository.saveAndFlush(flight);
        files.delete(fileId);
        return response(flight);
    }

    static Specification<ClientFlightEntity> specification(ClientFlightFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(filter.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("outboundAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("outboundAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (Specs.hasText(filter.airline())) {
                predicates.add(cb.like(cb.lower(root.get("airline")), Specs.likePattern(filter.airline()),
                        Specs.escapeChar()));
            }
            List<String> tokens = SearchText.tokens(filter.q());
            if (!tokens.isEmpty()) {
                // Todas as palavras no nome do MESMO passageiro, em qualquer ordem; ou o localizador exato.
                Subquery<Integer> traveler = query.subquery(Integer.class);
                Root<ClientFlightTravelerEntity> t = traveler.from(ClientFlightTravelerEntity.class);
                List<Predicate> match = new ArrayList<>();
                match.add(cb.equal(t.get("flight"), root));
                for (String token : tokens) {
                    match.add(cb.like(t.get("nameSearch"), Specs.likePattern(token), Specs.escapeChar()));
                }
                traveler.select(cb.literal(1)).where(match.toArray(Predicate[]::new));
                predicates.add(cb.or(cb.exists(traveler),
                        cb.equal(cb.upper(root.get("locator")), filter.q().trim().toUpperCase())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void apply(ClientFlightEntity flight, ClientFlightRequest request) {
        if (request.returnAt() != null && request.returnAt().isBefore(request.outboundAt())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "RETURN_BEFORE_OUTBOUND",
                    "A volta nao pode ser antes da ida.");
        }
        flight.setLocator(blankToNull(upper(request.locator())));
        flight.setAirline(request.airline().strip());
        flight.setOrigin(request.origin().strip());
        flight.setDestination(request.destination().strip());
        flight.setOutboundAt(request.outboundAt());
        flight.setOutboundFlightNumber(blankToNull(upper(request.outboundFlightNumber())));
        flight.setReturnAt(request.returnAt());
        flight.setReturnFlightNumber(blankToNull(upper(request.returnFlightNumber())));
        flight.setNotes(blankToNull(request.notes()));

        Map<UUID, ClientFlightTravelerEntity> existing = new HashMap<>();
        flight.getTravelers().forEach(t -> existing.put(t.getId(), t));
        List<ClientFlightTravelerEntity> travelers = new ArrayList<>();
        int order = 0;
        for (ClientFlightRequest.Traveler input : request.travelers()) {
            ClientFlightTravelerEntity previous = input.id() == null ? null : existing.get(input.id());
            ClientFlightTravelerEntity traveler = previous != null ? previous : new ClientFlightTravelerEntity();
            traveler.setFlight(flight);
            traveler.setSortOrder(order++);
            traveler.setFullName(input.fullName().strip().replaceAll("\\s+", " "));
            traveler.setNameSearch(SearchText.normalize(input.fullName()));
            traveler.setCpf(resolveCpf(input.cpf(), previous));
            traveler.setPhone(blankToNull(input.phone()));
            travelers.add(traveler);
        }
        flight.getTravelers().clear();
        flight.getTravelers().addAll(travelers);
    }

    /** null mantém o CPF guardado (a tela só tem o mascarado); vazio apaga; preenchido precisa ser válido. */
    static String resolveCpf(String input, ClientFlightTravelerEntity previous) {
        if (input == null) {
            return previous == null ? null : previous.getCpf();
        }
        String digits = Cpf.digits(input);
        if (digits == null) {
            return null;
        }
        if (!Cpf.isValid(digits)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CPF_INVALID", "CPF invalido.");
        }
        return digits;
    }

    static void checkVersion(ClientFlightEntity flight, Long version) {
        if (version == null || version != flight.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Voo alterado por outra pessoa. Recarregue antes de salvar.");
        }
    }

    private ClientFlightEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "CLIENT_FLIGHT_NOT_FOUND", "Voo nao encontrado."));
    }

    private ClientFlightResponse response(ClientFlightEntity f) {
        return new ClientFlightResponse(f.getId(), f.getLocator(), f.getAirline(), f.getOrigin(), f.getDestination(),
                f.getOutboundAt(), f.getOutboundFlightNumber(), f.getReturnAt(), f.getReturnFlightNumber(),
                f.getNotes(), f.getArchivedAt() != null, f.getArchivedAt(),
                f.getTravelers().stream().map(t -> new ClientFlightResponse.Traveler(t.getId(), t.getFullName(),
                        Cpf.mask(t.getCpf()), t.getPhone())).toList(),
                files.findAttached(FileCategory.FLIGHT_ATTACHMENT, f.getAttachmentIds()),
                f.getVersion(), f.getCreatedBy(), f.getCreatedAt(), f.getUpdatedAt());
    }

    public static ClientFlightSummary summary(ClientFlightEntity f) {
        return new ClientFlightSummary(f.getId(), f.getLocator(), f.getAirline(), f.getOrigin(), f.getDestination(),
                f.getOutboundAt(), f.getReturnAt(),
                f.getTravelers().stream().map(ClientFlightTravelerEntity::getFullName).toList(),
                f.getAttachmentIds().size(), f.getArchivedAt() != null);
    }

    private static String upper(String value) {
        return value == null ? null : value.strip().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
