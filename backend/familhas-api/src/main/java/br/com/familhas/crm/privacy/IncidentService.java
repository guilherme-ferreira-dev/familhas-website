package br.com.familhas.crm.privacy;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.api.BusinessException;

/** Registro de incidentes (ADR 0018; plano em docs/incident-response.md). */
@Service
public class IncidentService {

    private final IncidentRepository repository;

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list() {
        return repository.findAllByOrderByDetectedAtDesc().stream().map(IncidentResponse::of).toList();
    }

    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        IncidentEntity incident = new IncidentEntity();
        incident.setCreatedBy(AuditActor.current().displayName());
        apply(incident, request);
        return IncidentResponse.of(repository.saveAndFlush(incident));
    }

    @Transactional
    public IncidentResponse update(UUID id, IncidentRequest request) {
        IncidentEntity incident = repository.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                "INCIDENT_NOT_FOUND", "Incidente nao encontrado."));
        if (request.version() == null || request.version() != incident.getVersion()) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Incidente alterado por outra pessoa. Recarregue antes de salvar.");
        }
        apply(incident, request);
        return IncidentResponse.of(repository.saveAndFlush(incident));
    }

    private static void apply(IncidentEntity incident, IncidentRequest r) {
        if (r.status() == IncidentEntity.Status.ENCERRADO && r.closedAt() == null) {
            throw invalid("CLOSED_AT_REQUIRED", "Informe quando o incidente foi encerrado.");
        }
        if (r.status() != IncidentEntity.Status.ABERTO && r.containedAt() == null) {
            throw invalid("CONTAINED_AT_REQUIRED", "Informe quando o incidente foi contido.");
        }
        if (r.containedAt() != null && r.containedAt().isBefore(r.detectedAt())
                || r.closedAt() != null && r.closedAt().isBefore(r.detectedAt())) {
            throw invalid("INCIDENT_DATES_INVALID", "Contenção e encerramento nao podem ser antes da deteccao.");
        }
        incident.setTitle(r.title().strip());
        incident.setDescription(r.description().strip());
        incident.setSeverity(r.severity());
        incident.setStatus(r.status());
        incident.setPersonalData(r.personalData());
        incident.setDataCategories(blankToNull(r.dataCategories()));
        incident.setAffectedEstimate(r.affectedEstimate());
        incident.setActionsTaken(blankToNull(r.actionsTaken()));
        incident.setDetectedAt(r.detectedAt());
        incident.setContainedAt(r.containedAt());
        incident.setClosedAt(r.closedAt());
        incident.setAnpdNotifiedAt(r.anpdNotifiedAt());
        incident.setHoldersNotifiedAt(r.holdersNotifiedAt());
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
