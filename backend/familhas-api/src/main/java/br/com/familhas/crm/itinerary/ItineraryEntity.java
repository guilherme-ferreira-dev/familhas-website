package br.com.familhas.crm.itinerary;

import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Roteiro / proposta comercial (V22). internalAlerts nunca sai na proposta ao cliente. */
@Entity
@Table(name = "itineraries")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String destination;

    @Column(nullable = false)
    private int days;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "client_phone", length = 20)
    private String clientPhone;

    @Column(name = "travel_period", length = 120)
    private String travelPeriod;

    @Column(name = "price_text", length = 200)
    private String priceText;

    @Column(name = "payment_terms", length = 300)
    private String paymentTerms;

    @Column(length = 2000)
    private String observations;

    @Lob
    @Column(nullable = false)
    private String sections;

    @Column(name = "internal_alerts", length = 2000)
    private String internalAlerts;

    /** Ids das fotos (ITINERARY_PHOTO), separados por vírgula; até 4 para caber na página. */
    @Column(name = "photo_ids", length = 400)
    private String photoIds;

    @Column(name = "created_by", length = 180, updatable = false)
    private String createdBy;

    @Version
    @Column(nullable = false)
    private long version;
}
