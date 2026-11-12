package br.com.familhas.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.familhas.client.ClientEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.quote.QuoteEntity;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserva (V11). Snapshot de preços copiado da cotação; o status só muda pelos métodos de transição,
 * que aplicam a máquina de estados de {@link BookingStatus}.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class BookingEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientEntity client;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", nullable = false, unique = true)
    private QuoteEntity quote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_package_id")
    private TravelPackageEntity travelPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Setter(AccessLevel.NONE)
    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

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

    /** Prazo para pagar (ADR 0007, D3). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    @Setter(AccessLevel.NONE)
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    @Setter(AccessLevel.NONE)
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 30)
    @Setter(AccessLevel.NONE)
    private CancellationReason cancellationReason;

    /** Voo do catálogo curado com assentos bloqueados por esta reserva, ou null. */
    @Column(name = "held_flight_id")
    private UUID heldFlightId;

    @Column(name = "held_seats", nullable = false)
    private Integer heldSeats = 0;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BookingItemEntity> items = new ArrayList<>();

    public void addItem(BookingItemEntity item) {
        item.setBooking(this);
        item.setPosition(items.size());
        items.add(item);
    }

    /** PENDING_PAYMENT -> CONFIRMED. Retorna false (sem mudar nada) se a transição não é permitida. */
    public boolean confirm(Instant now) {
        if (!status.canTransitionTo(BookingStatus.CONFIRMED)) {
            return false;
        }
        status = BookingStatus.CONFIRMED;
        confirmedAt = now;
        return true;
    }

    /** PENDING_PAYMENT ou CONFIRMED -> CANCELLED. Retorna false (sem mudar nada) se não é permitido. */
    public boolean cancel(CancellationReason reason, Instant now) {
        if (!status.canTransitionTo(BookingStatus.CANCELLED)) {
            return false;
        }
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
        cancellationReason = reason;
        return true;
    }

    public boolean isPaymentWindowOpen(Instant now) {
        return status == BookingStatus.PENDING_PAYMENT && now.isBefore(expiresAt);
    }

    /** Zera o bloqueio e devolve quantos assentos precisam voltar para o voo. */
    public int takeHeldSeats() {
        int seats = heldSeats == null ? 0 : heldSeats;
        heldSeats = 0;
        return seats;
    }
}
