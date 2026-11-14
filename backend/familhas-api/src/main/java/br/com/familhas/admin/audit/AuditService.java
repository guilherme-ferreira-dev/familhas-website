package br.com.familhas.admin.audit;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.admin.AdminPeriod;
import br.com.familhas.common.PageResponse;
import br.com.familhas.common.Specs;

/**
 * Log de auditoria do Admin (ADR 0011). Grava numa transação própria (REQUIRES_NEW): o registro de uma escrita
 * que falhou também fica, e uma falha ao auditar nunca desfaz a ação, que já foi confirmada.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditActor actor, AuditEntry entry) {
        AuditLogEntity row = new AuditLogEntity();
        row.setActorSubject(truncate(actor.subject(), 64));
        row.setActorUsername(truncate(actor.username(), 180));
        row.setAction(entry.action());
        row.setResource(truncate(entry.resource(), 60));
        row.setResourceId(truncate(entry.resourceId(), 64));
        row.setHttpMethod(entry.httpMethod());
        row.setPath(truncate(entry.path(), 300));
        row.setStatus(entry.status());
        row.setDetails(truncate(entry.details(), 1000));
        row.setOccurredAt(clock.instant());
        repository.save(row);
        log.info("Auditoria: actor={} action={} resource={} resourceId={} status={}", actor.subject(),
                entry.action(), entry.resource(), entry.resourceId(), entry.status());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> findAll(AuditLogFilter filter, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(repository.findAll(toSpecification(filter), sorted), AuditLogResponse::of);
    }

    private static Specification<AuditLogEntity> toSpecification(AuditLogFilter filter) {
        List<Specification<AuditLogEntity>> specs = new ArrayList<>();
        if (Specs.hasText(filter.actor())) {
            String pattern = Specs.likePattern(filter.actor());
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("actorSubject")), pattern, Specs.escapeChar()),
                    cb.like(cb.lower(root.<String>get("actorUsername")), pattern, Specs.escapeChar())));
        }
        if (Specs.hasText(filter.resource())) {
            specs.add((root, query, cb) -> cb.equal(root.get("resource"), filter.resource().trim()));
        }
        if (filter.action() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("action"), filter.action()));
        }
        AdminPeriod period = AdminPeriod.of(filter.from(), filter.to());
        if (period.start() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), period.start()));
        }
        if (period.endExclusive() != null) {
            specs.add((root, query, cb) -> cb.lessThan(root.get("occurredAt"), period.endExclusive()));
        }
        return Specification.allOf(specs);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
