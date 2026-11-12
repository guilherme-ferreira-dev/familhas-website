package br.com.familhas.flight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.familhas.city.CityEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.common.Currencies;
import br.com.familhas.enums.EntityStatus;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Voo de ida e volta do catálogo curado (V9: rota, preço por passageiro, assentos e status). */
@Entity
@Table(name = "flights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false)
    private LocalDateTime boardingDate;

    @Column(nullable = false)
    private LocalDateTime returnDate;

    @Column(nullable = false, length = 120)
    private String boardingAirport;

    @Column(nullable = false, length = 120)
    private String returnAirport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_city_id", nullable = false)
    private CityEntity origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_city_id", nullable = false)
    private CityEntity destination;

    @Column(name = "price_per_passenger", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerPassenger;

    /** ISO 4217 do pricePerPassenger. */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = Currencies.DEFAULT;

    @Column(name = "seats_available", nullable = false)
    private Integer seatsAvailable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntityStatus status;
}
