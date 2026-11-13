package br.com.familhas.admin.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.com.familhas.admin.AdminPeriod;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Pagination;
import br.com.familhas.payment.PaymentEntity;

/** Planilha de pagamentos do Admin (ADR 0011): listagem filtrável e leitura em lotes para a exportação. */
@Service
public class AdminPaymentService {

    static final Set<String> SORTABLE = Set.of("createdAt", "paidAt", "amount", "updatedAt");
    static final int EXPORT_BATCH_SIZE = 500;

    private final AdminPaymentRepository repository;
    private final TransactionTemplate readOnly;

    public AdminPaymentService(AdminPaymentRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> findAll(AdminPaymentFilter filter, Pageable pageable) {
        Pagination.validateSort(pageable, SORTABLE);
        return PageResponse.of(repository.findAll(toSpecification(filter), pageable), AdminPaymentResponse::of);
    }

    @Transactional(readOnly = true)
    public AdminPaymentResponse findById(UUID id) {
        return repository.findById(id).map(AdminPaymentResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));
    }

    /**
     * Percorre todas as linhas do filtro em lotes, cada lote na própria transação de leitura, sem carregar tudo
     * em memória. Ordem estável (createdAt, id) para nenhuma linha repetir ou sumir entre lotes.
     */
    public void forEachRow(AdminPaymentFilter filter, Consumer<AdminPaymentResponse> consumer) {
        Specification<PaymentEntity> specification = toSpecification(filter);
        Sort order = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        int page = 0;
        boolean hasNext = true;
        while (hasNext) {
            Pageable pageable = PageRequest.of(page++, EXPORT_BATCH_SIZE, order);
            List<AdminPaymentResponse> rows = new ArrayList<>(EXPORT_BATCH_SIZE);
            Boolean next = readOnly.execute(status -> {
                Slice<PaymentEntity> slice = repository.findAll(specification, pageable);
                slice.forEach(payment -> rows.add(AdminPaymentResponse.of(payment)));
                return slice.hasNext();
            });
            rows.forEach(consumer);
            hasNext = Boolean.TRUE.equals(next);
        }
    }

    static Specification<PaymentEntity> toSpecification(AdminPaymentFilter filter) {
        List<Specification<PaymentEntity>> specs = new ArrayList<>();
        AdminPeriod period = AdminPeriod.of(filter.from(), filter.to());
        String dateAttribute = (filter.dateField() == null ? PaymentDateField.CREATED : filter.dateField()).attribute();
        if (period.start() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get(dateAttribute), period.start()));
        }
        if (period.endExclusive() != null) {
            specs.add((root, query, cb) -> cb.lessThan(root.get(dateAttribute), period.endExclusive()));
        }
        if (filter.status() != null && !filter.status().isEmpty()) {
            specs.add((root, query, cb) -> root.get("status").in(filter.status()));
        }
        if (filter.method() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("method"), filter.method()));
        }
        if (filter.installments() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("installments"), filter.installments()));
        }
        if (filter.bookingId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("booking").get("id"), filter.bookingId()));
        }
        if (filter.clientId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("booking").get("client").get("id"), filter.clientId()));
        }
        return Specification.allOf(specs);
    }
}
