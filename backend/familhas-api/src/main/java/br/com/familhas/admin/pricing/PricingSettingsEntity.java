package br.com.familhas.admin.pricing;

import java.math.BigDecimal;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Regras de preço salvas no Admin (V15). Linha única, id = 1. */
@Entity
@Table(name = "pricing_settings")
@Getter
@Setter
@NoArgsConstructor
public class PricingSettingsEntity extends AuditableEntity {

    public static final int SINGLE_ROW_ID = 1;

    @Id
    private Integer id;

    @Column(name = "margin_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal marginPercent;

    @Column(name = "service_fee_per_passenger", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceFeePerPassenger;

    @Column(name = "service_fee_currency", nullable = false, length = 3)
    private String serviceFeeCurrency;

    @Column(name = "updated_by", length = 180)
    private String updatedBy;
}
