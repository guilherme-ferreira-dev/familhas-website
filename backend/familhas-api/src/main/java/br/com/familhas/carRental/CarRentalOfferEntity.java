package br.com.familhas.carRental;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.familhas.city.CityEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.enums.CarCategory;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.enums.Transmission;

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

@Entity
@Table(name = "car_rental_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarRentalOfferEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private CityEntity city;

    @Column(nullable = false, length = 120)
    private String companyName;

    @Column(nullable = false, length = 120)
    private String carModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarCategory carCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Transmission transmission;

    @Column(nullable = false)
    private Integer seats;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerDay;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntityStatus status;
}
