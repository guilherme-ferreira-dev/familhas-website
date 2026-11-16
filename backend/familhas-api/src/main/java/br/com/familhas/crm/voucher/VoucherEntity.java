package br.com.familhas.crm.voucher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Voucher emitido (V21). Cancelar não apaga: o histórico fica. */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class VoucherEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "voucher_number", nullable = false, length = 20, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 20)
    private VoucherType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "client_cpf", length = 11)
    private String clientCpf;

    @Column(name = "client_phone", length = 20)
    private String clientPhone;

    @Column(length = 2000)
    private String notes;

    @Lob
    @Column(nullable = false)
    private String details;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "source_file_id")
    private UUID sourceFileId;

    @Column(name = "issued_by", length = 180, updatable = false)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;
}
