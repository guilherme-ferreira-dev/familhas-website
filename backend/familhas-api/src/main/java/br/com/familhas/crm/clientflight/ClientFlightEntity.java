package br.com.familhas.crm.clientflight;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Voo de cliente da agência (V19, voo_flights). Horários locais do bilhete, sem fuso. */
@Entity
@Table(name = "voo_flights")
@Getter
@Setter
@NoArgsConstructor
public class ClientFlightEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 20)
    private String locator;

    @Column(nullable = false, length = 60)
    private String airline;

    @Column(nullable = false, length = 60)
    private String origin;

    @Column(nullable = false, length = 60)
    private String destination;

    @Column(name = "outbound_at", nullable = false)
    private LocalDateTime outboundAt;

    @Column(name = "outbound_flight_number", length = 20)
    private String outboundFlightNumber;

    @Column(name = "return_at")
    private LocalDateTime returnAt;

    @Column(name = "return_flight_number", length = 20)
    private String returnFlightNumber;

    @Column(length = 2000)
    private String notes;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "flight", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ClientFlightTravelerEntity> travelers = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "voo_flight_attachments", joinColumns = @JoinColumn(name = "flight_id"))
    @Column(name = "file_id", nullable = false)
    private Set<UUID> attachmentIds = new LinkedHashSet<>();
}
