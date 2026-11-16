package br.com.familhas.crm.privacy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.crm.clientflight.ClientFlightEntity;
import br.com.familhas.crm.clientflight.ClientFlightTravelerEntity;
import br.com.familhas.crm.clientflight.ClientFlightTravelerRepository;
import br.com.familhas.crm.common.Cpf;
import br.com.familhas.crm.common.SearchText;
import br.com.familhas.crm.voucher.VoucherEntity;
import br.com.familhas.crm.voucher.VoucherRepository;

/**
 * Pedidos de titular (LGPD, art. 18; ADR 0018): relatório dos dados (acesso) e anonimização (eliminação quando
 * cabível). Sempre manual, por admin, com a identidade do titular confirmada e o protocolo do pedido na auditoria
 * (sem o CPF inteiro). Nada é apagado: nome, CPF, telefone e observações viram "Titular anonimizado" e vazio, para
 * não quebrar o histórico operacional.
 *
 * Fica como está: voo que ainda não terminou (a viagem precisa dos dados) e voucher dentro do prazo legal de
 * retenção ({@code familhas.privacy.voucher-retention}, premissa a validar com o jurídico).
 */
@Service
public class PrivacyService {

    static final String ANONYMIZED = "Titular anonimizado";

    private final ClientFlightTravelerRepository travelers;
    private final VoucherRepository vouchers;
    private final PrivacyProperties properties;
    private final ObjectProvider<AuditService> audit;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PrivacyService(ClientFlightTravelerRepository travelers, VoucherRepository vouchers,
            PrivacyProperties properties, ObjectProvider<AuditService> audit, TransactionTemplate tx,
            ObjectMapper objectMapper, Clock clock) {
        this.travelers = travelers;
        this.vouchers = vouchers;
        this.properties = properties;
        this.audit = audit;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public SubjectReport report(SubjectRequest request) {
        String cpf = validCpf(request.cpf());
        SubjectReport report = tx.execute(status -> new SubjectReport(cpf, clock.instant(),
                AuditActor.current().displayName(),
                travelers.findByCpf(cpf).stream().map(t -> {
                    ClientFlightEntity f = t.getFlight();
                    return new SubjectReport.Flight(f.getId(), t.getFullName(), t.getPhone(), f.getAirline(),
                            f.getOrigin(), f.getDestination(), f.getOutboundAt(), f.getReturnAt(),
                            f.getArchivedAt() != null);
                }).sorted(Comparator.comparing(SubjectReport.Flight::outboundAt)).toList(),
                vouchers.findByClientCpf(cpf).stream().map(v -> new SubjectReport.Voucher(v.getId(), v.getNumber(),
                        v.getType().name(), v.getStatus().name(), v.getClientName(), v.getClientPhone(),
                        v.getServiceDate(), v.getIssuedAt()))
                        .sorted(Comparator.comparing(SubjectReport.Voucher::issuedAt)).toList()));
        record(AuditAction.EXPORT, "relatorio de titular " + Cpf.mask(cpf) + " · pedido " + request.reference()
                + " · " + report.flights().size() + " voos, " + report.vouchers().size() + " vouchers");
        return report;
    }

    /** Prévia ({@code apply=false}) ou anonimização, numa transação só. */
    public AnonymizationReport anonymize(SubjectRequest request, boolean apply) {
        String cpf = validCpf(request.cpf());
        AnonymizationReport report = tx.execute(status -> {
            AnonymizationReport result = run(cpf);
            if (!apply) {
                status.setRollbackOnly();
            }
            return new AnonymizationReport(apply, result.travelersAnonymized(), result.vouchersAnonymized(),
                    result.kept());
        });
        if (apply) {
            record(AuditAction.UPDATE, "anonimizacao de titular " + Cpf.mask(cpf) + " · pedido " + request.reference()
                    + " · " + report.travelersAnonymized() + " passageiros, " + report.vouchersAnonymized()
                    + " vouchers; mantidos " + report.kept().size());
        }
        return report;
    }

    private AnonymizationReport run(String cpf) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(BusinessTime.ZONE));
        LocalDate today = BusinessTime.today(clock);
        List<AnonymizationReport.Kept> kept = new ArrayList<>();
        int travelersDone = 0;
        for (ClientFlightTravelerEntity t : travelers.findByCpf(cpf)) {
            ClientFlightEntity f = t.getFlight();
            LocalDateTime end = f.getReturnAt() != null ? f.getReturnAt() : f.getOutboundAt();
            if (end.isAfter(now)) {
                kept.add(new AnonymizationReport.Kept("Voo", f.getOrigin() + " → " + f.getDestination() + " em "
                        + f.getOutboundAt().toLocalDate(), "viagem ainda não terminou", end.toLocalDate()));
                continue;
            }
            t.setFullName(ANONYMIZED);
            t.setNameSearch(SearchText.normalize(ANONYMIZED));
            t.setCpf(null);
            t.setPhone(null);
            travelersDone++;
        }
        int vouchersDone = 0;
        for (VoucherEntity v : vouchers.findByClientCpf(cpf)) {
            LocalDate until = v.getIssuedAt().atZone(BusinessTime.ZONE).toLocalDate()
                    .plus(properties.voucherRetention());
            if (until.isAfter(today)) {
                kept.add(new AnonymizationReport.Kept("Voucher", v.getNumber(), "prazo legal de retenção", until));
                continue;
            }
            v.setClientName(ANONYMIZED);
            v.setClientCpf(null);
            v.setClientPhone(null);
            v.setNotes(null);
            v.setDetails(withoutPassengers(v.getDetails()));
            vouchersDone++;
        }
        travelers.flush();
        return new AnonymizationReport(false, travelersDone, vouchersDone, kept);
    }

    /** Os outros passageiros do voucher também são dados pessoais: saem junto. */
    private String withoutPassengers(String details) {
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(details);
            node.putNull("passengers");
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException | ClassCastException e) {
            throw new IllegalStateException("Detalhes ilegiveis no voucher", e);
        }
    }

    private static String validCpf(String input) {
        String digits = Cpf.digits(input);
        if (!Cpf.isValid(digits)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CPF_INVALID", "CPF invalido.");
        }
        return digits;
    }

    private void record(AuditAction action, String details) {
        AuditService service = audit.getIfAvailable();
        if (service != null) {
            service.record(AuditActor.current(), new AuditEntry(action, "crm/privacy", null, "POST",
                    "/api/crm/privacy", 200, details));
        }
    }
}
