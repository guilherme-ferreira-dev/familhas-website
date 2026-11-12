package br.com.familhas.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import br.com.familhas.pricing.PriceItemType;
import br.com.familhas.quote.QuoteItemEntity;
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

/** Item da reserva: cópia do item da cotação, com o custo do provedor (sem margem). */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingItemEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

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

    public static BookingItemEntity copyOf(QuoteItemEntity source) {
        BookingItemEntity item = new BookingItemEntity();
        item.setItemType(source.getItemType());
        item.setProvider(source.getProvider());
        item.setOfferId(source.getOfferId());
        item.setDescription(source.getDescription());
        item.setStartDate(source.getStartDate());
        item.setEndDate(source.getEndDate());
        item.setUnitPrice(source.getUnitPrice());
        item.setCurrency(source.getCurrency());
        item.setQuantity(source.getQuantity());
        item.setSubtotal(source.getSubtotal());
        return item;
    }
}
