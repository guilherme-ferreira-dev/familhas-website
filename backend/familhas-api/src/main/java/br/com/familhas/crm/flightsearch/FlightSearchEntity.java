package br.com.familhas.crm.flightsearch;

import java.time.Instant;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pesquisa feita (V20). A mesma pesquisa de novo só renova {@code lastRunAt}. */
@Entity
@Table(name = "flight_searches")
@Getter
@Setter
@NoArgsConstructor
public class FlightSearchEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "params_hash", nullable = false, length = 64, updatable = false)
    private String paramsHash;

    @Column(nullable = false, length = 1000, updatable = false)
    private String params;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(name = "run_by", length = 180)
    private String runBy;

    @Column(name = "last_run_at", nullable = false)
    private Instant lastRunAt;
}
