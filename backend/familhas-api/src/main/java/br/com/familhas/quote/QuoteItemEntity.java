package br.com.familhas.quote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import br.com.familhas.pricing.PriceItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Item da cotação com o custo do provedor (sem margem) no momento do cálculo. */
@Entity
@Table(name = "quote_items")
@Getter
@Setter
@NoArgsConstructor
public class QuoteItemEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false)
    private QuoteEntity quote;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private PriceItemType itemType;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "offer_id", nullable = false, length = 500)
    private String offerId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
