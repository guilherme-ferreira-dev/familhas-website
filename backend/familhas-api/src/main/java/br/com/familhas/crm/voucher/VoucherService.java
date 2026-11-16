package br.com.familhas.crm.voucher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.common.Specs;
import br.com.familhas.crm.company.CompanyProperties;
import br.com.familhas.crm.common.Cpf;
import br.com.familhas.crm.file.CrmFileResponse;
import br.com.familhas.crm.file.CrmFileService;
import br.com.familhas.crm.file.FileCategory;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationNotConfiguredException;
import br.com.familhas.crm.integration.IntegrationRegistry;
import jakarta.persistence.criteria.Predicate;

/** Emitir Voucher (ADR 0015): emissão, correção, cancelamento, histórico e documento A4. */
@Service
public class VoucherService {

    static final Set<String> SORTABLE = Set.of("issuedAt", "serviceDate", "clientName", "number");

    private final VoucherRepository repository;
    private final CrmFileService files;
    private final CompanyProperties company;
    private final IntegrationRegistry integrations;
    private final ObjectProvider<VoucherExtractor> extractor;
    private final ObjectProvider<AuditService> audit;
    private final ObjectMapper objectMapper;

    public VoucherService(VoucherRepository repository, CrmFileService files, CompanyProperties company,
            IntegrationRegistry integrations, ObjectProvider<VoucherExtractor> extractor,
            ObjectProvider<AuditService> audit, ObjectMapper objectMapper) {
        this.repository = repository;
        this.files = files;
        this.company = company;
        this.integrations = integrations;
        this.extractor = extractor;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<VoucherSummary> search(String q, VoucherType type, VoucherStatus status, LocalDate from,
            LocalDate to, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "issuedAt");
        Specification<VoucherEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (Specs.hasText(q)) {
                p.add(cb.or(cb.like(cb.lower(root.get("clientName")), Specs.likePattern(q), Specs.escapeChar()),
                        cb.equal(root.get("number"), q.trim().toUpperCase())));
            }
            if (type != null) {
                p.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("issuedAt"), from.atStartOfDay(BusinessTime.ZONE).toInstant()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("issuedAt"), to.plusDays(1).atStartOfDay(BusinessTime.ZONE).toInstant()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")))),
                this::summary);
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(UUID id) {
        return response(find(id));
    }

    @Transactional
    public VoucherResponse issue(VoucherRequest request) {
        VoucherEntity voucher = new VoucherEntity();
        voucher.setNumber("VF-%06d".formatted(repository.nextNumber()));
        voucher.setStatus(VoucherStatus.ISSUED);
        voucher.setIssuedBy(AuditActor.current().displayName());
        voucher.setIssuedAt(Instant.now());
        apply(voucher, request);
        return response(repository.saveAndFlush(voucher));
    }

    @Transactional
    public VoucherResponse update(UUID id, VoucherRequest request) {
        VoucherEntity voucher = find(id);
        if (request.version() == null || request.version() != voucher.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Voucher alterado por outra pessoa. Recarregue antes de salvar.");
        }
        if (voucher.getStatus() == VoucherStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_CANCELLED",
                    "Voucher cancelado nao pode ser alterado.");
        }
        apply(voucher, request);
        return response(repository.saveAndFlush(voucher));
    }

    @Transactional
    public VoucherResponse cancel(UUID id) {
        VoucherEntity voucher = find(id);
        if (voucher.getStatus() != VoucherStatus.CANCELLED) {
            voucher.setStatus(VoucherStatus.CANCELLED);
            voucher.setCancelledAt(Instant.now());
        }
        return response(repository.saveAndFlush(voucher));
    }

    /** Documento para impressão A4, com o CPF inteiro: registrado na auditoria (DOWNLOAD). */
    @Transactional(readOnly = true)
    public VoucherDocument document(UUID id) {
        VoucherEntity voucher = find(id);
        AuditService service = audit.getIfAvailable();
        if (service != null) {
            service.record(AuditActor.current(), new AuditEntry(AuditAction.DOWNLOAD, "crm/vouchers", id.toString(),
                    "GET", "/api/crm/vouchers/" + id + "/document", 200, "documento A4 " + voucher.getNumber()));
        }
        return new VoucherDocument(company, response(voucher), formatCpf(voucher.getClientCpf()));
    }

    /**
     * Extração por IA do PDF/imagem de origem. Sem provedor configurado: 503 INTEGRATION_NOT_CONFIGURED e a tela
     * segue no preenchimento manual. Nome, CPF, telefone e observações nunca são extraídos.
     */
    public VoucherDetails extract(VoucherType type, UUID fileId) {
        integrations.require(CrmIntegration.AI_TEXT);
        VoucherExtractor available = extractor.getIfAvailable();
        if (available == null) {
            throw new IntegrationNotConfiguredException(CrmIntegration.AI_TEXT);
        }
        if (files.categoryOf(fileId) != FileCategory.VOUCHER_SOURCE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_CATEGORY_MISMATCH",
                    "O arquivo de origem precisa ser um envio de voucher.");
        }
        return available.extract(type, files.readContent(fileId), files.get(fileId).contentType());
    }

    private void apply(VoucherEntity voucher, VoucherRequest request) {
        List<String> missing = request.details().missingFor(request.type());
        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "VOUCHER_FIELDS_MISSING",
                    "Campos obrigatorios para o tipo: " + String.join(", ", missing), Map.of("fields", missing));
        }
        VoucherDetails details = request.details();
        if (details.endDate() != null && details.serviceDate() != null
                && details.endDate().isBefore(details.serviceDate())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DATES",
                    "A data final nao pode ser antes da inicial.");
        }
        if (request.sourceFileId() != null && files.categoryOf(request.sourceFileId()) != FileCategory.VOUCHER_SOURCE) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_CATEGORY_MISMATCH",
                    "O arquivo de origem precisa ser um envio de voucher.");
        }
        voucher.setType(request.type());
        voucher.setClientName(request.clientName().strip().replaceAll("\\s+", " "));
        voucher.setClientCpf(cpf(request.clientCpf(), voucher.getId() == null ? null : voucher.getClientCpf()));
        voucher.setClientPhone(blankToNull(request.clientPhone()));
        voucher.setNotes(blankToNull(request.notes()));
        voucher.setServiceDate(details.serviceDate());
        voucher.setSourceFileId(request.sourceFileId());
        try {
            voucher.setDetails(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cpf(String input, String current) {
        if (input == null) {
            return current;
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

    static String formatCpf(String digits) {
        if (digits == null || digits.length() != 11) {
            return null;
        }
        return digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-"
                + digits.substring(9);
    }

    private VoucherEntity find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "VOUCHER_NOT_FOUND", "Voucher nao encontrado."));
    }

    private VoucherDetails details(VoucherEntity voucher) {
        try {
            return objectMapper.readValue(voucher.getDetails(), VoucherDetails.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Detalhes ilegiveis no voucher " + voucher.getNumber(), e);
        }
    }

    private VoucherResponse response(VoucherEntity v) {
        CrmFileResponse source = v.getSourceFileId() == null ? null
                : files.findAttached(FileCategory.VOUCHER_SOURCE, List.of(v.getSourceFileId())).stream().findFirst()
                        .orElse(null);
        return new VoucherResponse(v.getId(), v.getNumber(), v.getType(), v.getStatus(), v.getClientName(),
                Cpf.mask(v.getClientCpf()), v.getClientPhone(), v.getNotes(), details(v), v.getServiceDate(), source,
                v.getIssuedBy(), v.getIssuedAt(), v.getCancelledAt(), v.getVersion());
    }

    private VoucherSummary summary(VoucherEntity v) {
        return new VoucherSummary(v.getId(), v.getNumber(), v.getType(), v.getStatus(), v.getClientName(),
                details(v).provider(), v.getServiceDate(), v.getIssuedBy(), v.getIssuedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
