package br.com.familhas.quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.familhas.client.ClientEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.travelPackage.TravelPackageEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cotação (V10). Snapshot do preço no momento do cálculo; nunca é recalculada. */
@Entity
@Table(name = "quotes")
@Getter
@Setter
@NoArgsConstructor
public class QuoteEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_package_id")
    private TravelPackageEntity travelPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status;

    @Column(nullable = false)
    private Integer passengers;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "components_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal componentsTotal;

    @Column(name = "margin_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal marginPercent;

    @Column(name = "margin_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal marginAmount;

    @Column(name = "promotion_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal promotionPercent;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "service_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceFeeAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuoteItemEntity> items = new ArrayList<>();

    public void addItem(QuoteItemEntity item) {
        item.setQuote(this);
        item.setPosition(items.size());
        items.add(item);
    }

    /** OPEN vira EXPIRED quando o prazo passa (avaliado na leitura). Retorna true se mudou. */
    public boolean expireIfDue(Instant now) {
        if (status == QuoteStatus.OPEN && !now.isBefore(expiresAt)) {
            status = QuoteStatus.EXPIRED;
            return true;
        }
        return false;
    }
}
