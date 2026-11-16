package br.com.familhas.crm.backup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.familhas.admin.audit.AuditAction;
import br.com.familhas.admin.audit.AuditActor;
import br.com.familhas.admin.audit.AuditEntry;
import br.com.familhas.admin.audit.AuditService;
import br.com.familhas.api.BusinessException;
import br.com.familhas.common.BusinessTime;
import br.com.familhas.crm.clientflight.ClientFlightEntity;
import br.com.familhas.crm.clientflight.ClientFlightRepository;
import br.com.familhas.crm.file.FileStorage;
import br.com.familhas.crm.flightsearch.FlightSearchGroupRepository;
import br.com.familhas.crm.flightsearch.FlightSearchRepository;
import br.com.familhas.crm.integration.CrmIntegration;
import br.com.familhas.crm.integration.IntegrationRegistry;
import br.com.familhas.crm.itinerary.ItineraryRepository;
import br.com.familhas.crm.team.TeamProperties;
import br.com.familhas.crm.voucher.VoucherRepository;

/**
 * Backup e restauração (JSON: uma tela exporta e restaura todas as tabelas de negócio num arquivo; ADR 0017).
 *
 * Restaurar nunca sobrescreve: registro que existe fica como está (inclusive se foi editado depois do backup); só
 * volta o que foi apagado. Tudo numa transação: ou entra tudo, ou nada. Os ids originais voltam, então restaurar o
 * mesmo arquivo duas vezes não duplica nada.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    static final long MAX_RESTORE_BYTES = 50L * 1024 * 1024;

    private final ClientFlightRepository flights;
    private final FlightSearchGroupRepository groups;
    private final FlightSearchRepository searches;
    private final VoucherRepository vouchers;
    private final ItineraryRepository itineraries;
    private final CrmBackupRepository stored;
    private final FileStorage storage;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final BackupProperties properties;
    private final TeamProperties team;
    private final IntegrationRegistry integrations;
    private final ObjectProvider<JavaMailSender> mail;
    private final ObjectProvider<AuditService> audit;
    private final Clock clock;

    public BackupService(ClientFlightRepository flights, FlightSearchGroupRepository groups,
            FlightSearchRepository searches, VoucherRepository vouchers, ItineraryRepository itineraries,
            CrmBackupRepository stored, FileStorage storage, JdbcTemplate jdbc, TransactionTemplate tx,
            ObjectMapper objectMapper, BackupProperties properties, TeamProperties team,
            IntegrationRegistry integrations, ObjectProvider<JavaMailSender> mail, ObjectProvider<AuditService> audit,
            Clock clock) {
        this.flights = flights;
        this.groups = groups;
        this.searches = searches;
        this.vouchers = vouchers;
        this.itineraries = itineraries;
        this.stored = stored;
        this.storage = storage;
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.team = team;
        this.integrations = integrations;
        this.mail = mail;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------- Exportar ----------

    /** Arquivo completo, já com o hash. Auditado como EXPORT. */
    public byte[] export(String reason) {
        BackupFile.Tables tables = tx.execute(status -> snapshot());
        BackupFile file = new BackupFile(BackupFile.FORMAT, BackupFile.VERSION, clock.instant(),
                AuditActor.current().displayName(), tables, sha256(tables));
        record(AuditAction.EXPORT, null, 200, reason + ": " + tables.count() + " registros");
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    BackupFile.Tables snapshot() {
        Comparator<Instant> order = Comparator.nullsFirst(Comparator.naturalOrder());
        List<BackupFile.ClientFlight> clientFlights = flights.findAll().stream()
                .sorted(Comparator.comparing(ClientFlightEntity::getCreatedAt, order))
                .map(f -> new BackupFile.ClientFlight(f.getId(), f.getLocator(), f.getAirline(), f.getOrigin(),
                        f.getDestination(), f.getOutboundAt(), f.getOutboundFlightNumber(), f.getReturnAt(),
                        f.getReturnFlightNumber(), f.getNotes(), f.getArchivedAt(), f.getCreatedBy(), f.getVersion(),
                        f.getCreatedAt(), f.getUpdatedAt(), f.getTravelers().stream()
                                .map(t -> new BackupFile.Traveler(t.getId(), t.getSortOrder(), t.getFullName(),
                                        t.getNameSearch(), t.getCpf(), t.getPhone()))
                                .toList()))
                .toList();
        List<BackupFile.FlightSearchGroup> groupRows = groups.findAll().stream()
                .sorted(Comparator.comparing(g -> g.getCreatedAt(), order))
                .map(g -> new BackupFile.FlightSearchGroup(g.getId(), g.getName(), g.getDestinations(),
                        g.getCreatedAt(), g.getUpdatedAt()))
                .toList();
        List<BackupFile.FlightSearch> searchRows = searches.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getCreatedAt(), order))
                .map(s -> new BackupFile.FlightSearch(s.getId(), s.getParamsHash(), s.getParams(), s.getSummary(),
                        s.getRunBy(), s.getLastRunAt(), s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
        List<BackupFile.Voucher> voucherRows = vouchers.findAll().stream()
                .sorted(Comparator.comparing(v -> v.getCreatedAt(), order))
                .map(v -> new BackupFile.Voucher(v.getId(), v.getNumber(), v.getType().name(), v.getStatus().name(),
                        v.getClientName(), v.getClientCpf(), v.getClientPhone(), v.getNotes(), v.getDetails(),
                        v.getServiceDate(), v.getIssuedBy(), v.getIssuedAt(), v.getCancelledAt(), v.getVersion(),
                        v.getCreatedAt(), v.getUpdatedAt()))
                .toList();
        List<BackupFile.Itinerary> itineraryRows = itineraries.findAll().stream()
                .sorted(Comparator.comparing(i -> i.getCreatedAt(), order))
                .map(i -> new BackupFile.Itinerary(i.getId(), i.getDestination(), i.getDays(), i.getClientName(),
                        i.getClientPhone(), i.getTravelPeriod(), i.getPriceText(), i.getPaymentTerms(),
                        i.getObservations(), i.getSections(), i.getInternalAlerts(), i.getCreatedBy(), i.getVersion(),
                        i.getCreatedAt(), i.getUpdatedAt()))
                .toList();
        return new BackupFile.Tables(clientFlights, groupRows, searchRows, voucherRows, itineraryRows);
    }

    // ---------- Guardar e avisar por e-mail ----------

    /**
     * Gera o backup, guarda criptografado e manda por e-mail só o link da tela de backup (exige login). O arquivo
     * nunca vai anexado: tem CPFs.
     */
    public StoredBackup emailBackup(String to) {
        integrations.require(CrmIntegration.EMAIL);
        BackupCipher cipher = cipher();
        String recipient = to == null || to.isBlank() ? properties.defaultEmail() : to.strip();
        byte[] content = export("backup guardado e enviado para " + recipient);
        String key = UUID.randomUUID().toString();
        try {
            storage.put(key, new ByteArrayInputStream(cipher.encrypt(content)));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao guardar o backup", e);
        }
        CrmBackupEntity entity = new CrmBackupEntity();
        entity.setStorageKey(key);
        entity.setSizeBytes(content.length);
        entity.setSha256(hex(content));
        entity.setRecordCount(count(content));
        entity.setCreatedBy(AuditActor.current().displayName());
        entity.setEmailedTo(recipient);
        entity.setExpiresAt(clock.instant().plus(properties.retention()));
        CrmBackupEntity saved = tx.execute(status -> stored.saveAndFlush(entity));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromEmail());
        message.setTo(recipient);
        message.setSubject("Backup do CRM Voe Familhas · " + BusinessTime.today(clock));
        message.setText("""
                O backup do CRM foi gerado por %s com %d registros.

                Para baixar, entre no CRM com a sua conta (o arquivo não vai anexado porque contém dados pessoais):
                %sbackup?baixar=%s

                O backup fica disponível até %s.
                """.formatted(saved.getCreatedBy(), saved.getRecordCount(), team.crmUrl(), saved.getId(),
                saved.getExpiresAt().atZone(BusinessTime.ZONE).toLocalDate()));
        try {
            mail.getObject().send(message);
        } catch (RuntimeException e) {
            log.warn("Backup {} guardado, mas o e-mail para {} nao saiu: {}", saved.getId(), recipient, e.getMessage());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "BACKUP_EMAIL_FAILED",
                    "Backup guardado, mas o e-mail nao saiu. Ele continua disponivel nesta tela.");
        }
        return StoredBackup.of(saved);
    }

    public List<StoredBackup> listStored() {
        return stored.findByExpiresAtAfterOrderByCreatedAtDesc(clock.instant()).stream().map(StoredBackup::of)
                .toList();
    }

    public byte[] downloadStored(UUID id) {
        CrmBackupEntity entity = stored.findById(id).filter(b -> b.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "BACKUP_NOT_FOUND",
                        "Backup nao encontrado ou vencido."));
        try (InputStream in = storage.open(entity.getStorageKey())) {
            byte[] content = cipher().decrypt(in.readAllBytes());
            record(AuditAction.EXPORT, id.toString(), 200, "download do backup guardado");
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Backup guardado sem conteudo", e);
        }
    }

    /** Apaga os backups guardados vencidos (banco e storage). */
    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    public int purgeExpired() {
        List<CrmBackupEntity> expired = stored.findByExpiresAtBefore(clock.instant());
        for (CrmBackupEntity backup : expired) {
            try {
                storage.delete(backup.getStorageKey());
            } catch (IOException e) {
                log.warn("Backup vencido sem apagar do storage: {}", backup.getId());
            }
            stored.delete(backup);
        }
        return expired.size();
    }

    // ---------- Restaurar ----------

    /** Prévia ({@code apply=false}) ou restauração. Arquivo inválido, de outra versão ou alterado: 422. */
    public RestoreReport restore(byte[] content, boolean apply) {
        if (content.length > MAX_RESTORE_BYTES) {
            throw invalid("BACKUP_TOO_LARGE", "Arquivo maior que 50 MB.");
        }
        BackupFile file;
        try {
            file = objectMapper.readValue(content, BackupFile.class);
        } catch (IOException e) {
            throw invalid("BACKUP_INVALID", "O arquivo nao e um backup do CRM.");
        }
        if (!BackupFile.FORMAT.equals(file.format()) || file.tables() == null) {
            throw invalid("BACKUP_INVALID", "O arquivo nao e um backup do CRM.");
        }
        if (file.version() != BackupFile.VERSION) {
            throw invalid("BACKUP_VERSION", "Versao de backup nao suportada: " + file.version() + ".");
        }
        if (!sha256(file.tables()).equals(file.sha256())) {
            throw invalid("BACKUP_CHECKSUM", "O arquivo foi alterado ou esta corrompido (hash nao confere).");
        }
        RestoreReport report = tx.execute(status -> {
            RestoreReport result = run(file, apply);
            if (!apply) {
                status.setRollbackOnly();
            }
            return result;
        });
        if (apply) {
            record(AuditAction.RESTORE, null, 200, "restaurados " + report.restored() + " registros do backup de "
                    + file.createdAt());
        }
        return report;
    }

    private RestoreReport run(BackupFile file, boolean apply) {
        BackupFile.Tables t = file.tables();
        List<RestoreReport.TableResult> results = new ArrayList<>();

        Counter c = new Counter("Voos Clientes", t.clientFlights());
        for (BackupFile.ClientFlight f : safe(t.clientFlights())) {
            if (exists("voo_flights", f.id())) {
                c.kept++;
                continue;
            }
            c.restored++;
            if (apply) {
                jdbc.update("""
                        insert into voo_flights (id, locator, airline, origin, destination, outbound_at,
                          outbound_flight_number, return_at, return_flight_number, notes, archived_at, created_by,
                          version, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                        raw(f.id()), f.locator(), f.airline(), f.origin(), f.destination(), local(f.outboundAt()),
                        f.outboundFlightNumber(), local(f.returnAt()), f.returnFlightNumber(), f.notes(),
                        zoned(f.archivedAt()), f.createdBy(), f.version(), zoned(f.createdAt()), zoned(f.updatedAt()));
                for (BackupFile.Traveler tr : safe(f.travelers())) {
                    jdbc.update("""
                            insert into voo_travelers (id, flight_id, sort_order, full_name, name_search, cpf, phone)
                            values (?,?,?,?,?,?,?)""", raw(tr.id()), raw(f.id()), tr.sortOrder(), tr.fullName(),
                            tr.nameSearch(), tr.cpf(), tr.phone());
                }
            }
        }
        results.add(c.result());

        c = new Counter("Grupos de destinos", t.flightSearchGroups());
        for (BackupFile.FlightSearchGroup g : safe(t.flightSearchGroups())) {
            if (exists("flight_search_groups", g.id())) {
                c.kept++;
            } else if (taken("select count(*) from flight_search_groups where lower(name) = lower(?)", g.name())) {
                c.conflicts++;
            } else {
                c.restored++;
                if (apply) {
                    jdbc.update("insert into flight_search_groups (id, name, destinations, created_at, updated_at)"
                            + " values (?,?,?,?,?)", raw(g.id()), g.name(), g.destinations(), zoned(g.createdAt()),
                            zoned(g.updatedAt()));
                }
            }
        }
        results.add(c.result());

        c = new Counter("Pesquisas de voos", t.flightSearches());
        for (BackupFile.FlightSearch s : safe(t.flightSearches())) {
            if (exists("flight_searches", s.id())) {
                c.kept++;
            } else if (taken("select count(*) from flight_searches where params_hash = ?", s.paramsHash())) {
                c.conflicts++;
            } else {
                c.restored++;
                if (apply) {
                    jdbc.update("""
                            insert into flight_searches (id, params_hash, params, summary, run_by, last_run_at,
                              created_at, updated_at) values (?,?,?,?,?,?,?,?)""", raw(s.id()), s.paramsHash(),
                            s.params(), s.summary(), s.runBy(), zoned(s.lastRunAt()), zoned(s.createdAt()),
                            zoned(s.updatedAt()));
                }
            }
        }
        results.add(c.result());

        c = new Counter("Vouchers", t.vouchers());
        for (BackupFile.Voucher v : safe(t.vouchers())) {
            if (exists("vouchers", v.id())) {
                c.kept++;
            } else if (taken("select count(*) from vouchers where voucher_number = ?", v.number())) {
                c.conflicts++;
            } else {
                c.restored++;
                if (apply) {
                    jdbc.update("""
                            insert into vouchers (id, voucher_number, voucher_type, status, client_name, client_cpf,
                              client_phone, notes, details, service_date, source_file_id, issued_by, issued_at,
                              cancelled_at, version, created_at, updated_at)
                            values (?,?,?,?,?,?,?,?,?,?,null,?,?,?,?,?,?)""", raw(v.id()), v.number(), v.type(),
                            v.status(), v.clientName(), v.clientCpf(), v.clientPhone(), v.notes(), v.details(),
                            date(v.serviceDate()), v.issuedBy(), zoned(v.issuedAt()), zoned(v.cancelledAt()),
                            v.version(), zoned(v.createdAt()), zoned(v.updatedAt()));
                }
            }
        }
        results.add(c.result());

        c = new Counter("Roteiros", t.itineraries());
        for (BackupFile.Itinerary i : safe(t.itineraries())) {
            if (exists("itineraries", i.id())) {
                c.kept++;
                continue;
            }
            c.restored++;
            if (apply) {
                jdbc.update("""
                        insert into itineraries (id, destination, days, client_name, client_phone, travel_period,
                          price_text, payment_terms, observations, sections, internal_alerts, photo_ids, created_by,
                          version, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?,?,null,?,?,?,?)""",
                        raw(i.id()), i.destination(), i.days(), i.clientName(), i.clientPhone(), i.travelPeriod(),
                        i.priceText(), i.paymentTerms(), i.observations(), i.sections(), i.internalAlerts(),
                        i.createdBy(), i.version(), zoned(i.createdAt()), zoned(i.updatedAt()));
            }
        }
        results.add(c.result());
        return new RestoreReport(apply, file.createdAt(), file.createdBy(), results);
    }

    // ---------- apoio ----------

    String sha256(BackupFile.Tables tables) {
        try {
            return hex(objectMapper.writeValueAsBytes(tables));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int count(byte[] content) {
        try {
            return objectMapper.readValue(content, BackupFile.class).tables().count();
        } catch (IOException e) {
            return 0;
        }
    }

    private BackupCipher cipher() {
        if (properties.encryptionKey() == null || properties.encryptionKey().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "BACKUP_KEY_NOT_CONFIGURED",
                    "Defina BACKUP_ENCRYPTION_KEY no servidor para guardar backups. O download direto continua.");
        }
        return new BackupCipher(properties.encryptionKey());
    }

    private void record(AuditAction action, String id, int status, String details) {
        AuditService service = audit.getIfAvailable();
        if (service != null) {
            service.record(AuditActor.current(), new AuditEntry(action, "crm/backups", id,
                    action == AuditAction.RESTORE ? "POST" : "GET", "/api/crm/backups", status, details));
        }
    }

    private boolean exists(String table, UUID id) {
        return taken("select count(*) from " + table + " where id = ?", raw(id));
    }

    private boolean taken(String sql, Object value) {
        Integer n = jdbc.queryForObject(sql, Integer.class, value);
        return n != null && n > 0;
    }

    static byte[] raw(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .array();
    }

    private static Timestamp local(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static Object zoned(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private static final class Counter {
        final String table;
        final int total;
        int restored;
        int kept;
        int conflicts;

        Counter(String table, List<?> rows) {
            this.table = table;
            this.total = rows == null ? 0 : rows.size();
        }

        RestoreReport.TableResult result() {
            return new RestoreReport.TableResult(table, total, restored, kept, conflicts);
        }
    }
}
