package br.com.familhas.crm.itinerary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Specs;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;

/** Gerar Roteiro (ADR 0016, D4): proposta em uma página A4, editada pela equipe, com o modelo mestre conferido. */
@Service
public class ItineraryService {

    private final ItineraryRepository repository;
    private final CrmFileService files;
    private final ObjectMapper objectMapper;

    public ItineraryService(ItineraryRepository repository, CrmFileService files, ObjectMapper objectMapper) {
        this.repository = repository;
        this.files = files;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItinerarySummary> search(String q, Pageable pageable) {
        String pattern = Specs.hasText(q) ? Specs.likePattern(q) : null;
        return PageResponse.of(repository.search(pattern, PageRequest.of(pageable.getPageNumber(),
                pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by("id")))),
                i -> new ItinerarySummary(i.getId(), i.getDestination(), i.getDays(), i.getClientName(),
                        i.getTravelPeriod(), i.getCreatedBy(), i.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public ItineraryResponse get(UUID id) {
        return response(find(id));
    }

    @Transactional
    public ItineraryResponse create(ItineraryRequest request) {
        ItineraryEntity itinerary = new ItineraryEntity();
        itinerary.setCreatedBy(AuditActor.current().displayName());
        apply(itinerary, request);
        return response(repository.saveAndFlush(itinerary));
    }

    @Transactional
    public ItineraryResponse update(UUID id, ItineraryRequest request) {
        ItineraryEntity itinerary = find(id);
        if (request.version() == null || request.version() != itinerary.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Roteiro alterado por outra pessoa. Recarregue antes de salvar.");
        }
        Set<UUID> before = photoIds(itinerary);
        apply(itinerary, request);
        repository.saveAndFlush(itinerary);
        before.removeAll(photoIds(itinerary));
        before.forEach(files::delete);
        return response(itinerary);
    }

    @Transactional
    public void delete(UUID id) {
        ItineraryEntity itinerary = find(id);
        Set<UUID> photos = photoIds(itinerary);
        repository.delete(itinerary);
        repository.flush();
        photos.forEach(files::delete);
    }

    private void apply(ItineraryEntity itinerary, ItineraryRequest request) {
        if (request.sections().days() != null) {
            for (ItinerarySections.Day day : request.sections().days()) {
                if (day.day() > request.days()) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DAY_OUT_OF_RANGE",
                            "O roteiro tem " + request.days() + " dias; ha um dia " + day.day() + ".");
                }
            }
        }
        List<UUID> photos = request.photoIds() == null ? List.of() : request.photoIds().stream().distinct().toList();
        for (UUID photo : photos) {
            if (files.categoryOf(photo) != FileCategory.ITINERARY_PHOTO
                    || !files.get(photo).contentType().startsWith("image/")) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_CATEGORY_MISMATCH",
                        "Use fotos enviadas para o roteiro.");
            }
        }
        itinerary.setDestination(request.destination().strip());
        itinerary.setDays(request.days());
        itinerary.setClientName(blankToNull(request.clientName()));
        itinerary.setClientPhone(blankToNull(request.clientPhone()));
        itinerary.setTravelPeriod(blankToNull(request.travelPeriod()));
        itinerary.setPriceText(blankToNull(request.priceText()));
        itinerary.setPaymentTerms(blankToNull(request.paymentTerms()));
        itinerary.setObservations(blankToNull(request.observations()));
        itinerary.setInternalAlerts(blankToNull(request.internalAlerts()));
        itinerary.setPhotoIds(photos.isEmpty() ? null
                : photos.stream().map(UUID::toString).collect(Collectors.joining(",")));
        try {
            itinerary.setSections(objectMapper.writeValueAsString(request.sections()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ItineraryEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "ITINERARY_NOT_FOUND", "Roteiro nao encontrado."));
    }

    private ItineraryResponse response(ItineraryEntity i) {
        ItinerarySections sections;
        try {
            sections = objectMapper.readValue(i.getSections(), ItinerarySections.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Secoes ilegiveis no roteiro " + i.getId(), e);
        }
        List<UUID> order = i.getPhotoIds() == null ? List.of()
                : Arrays.stream(i.getPhotoIds().split(",")).map(UUID::fromString).toList();
        List<CrmFileResponse> found = files.findAttached(FileCategory.ITINERARY_PHOTO, order);
        List<CrmFileResponse> photos = order.stream()
                .flatMap(id -> found.stream().filter(f -> f.id().equals(id))).toList();
        return new ItineraryResponse(i.getId(), i.getDestination(), i.getDays(), i.getClientName(),
                i.getClientPhone(), i.getTravelPeriod(), i.getPriceText(), i.getPaymentTerms(), i.getObservations(),
                sections, i.getInternalAlerts(), photos, ItineraryRules.warnings(sections), i.getCreatedBy(),
                i.getCreatedAt(), i.getUpdatedAt(), i.getVersion());
    }

    private static Set<UUID> photoIds(ItineraryEntity i) {
        return i.getPhotoIds() == null ? new HashSet<>()
                : Arrays.stream(i.getPhotoIds().split(",")).map(UUID::fromString)
                        .collect(Collectors.toCollection(HashSet::new));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
