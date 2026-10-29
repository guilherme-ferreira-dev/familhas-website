package br.com.familhas.travelPackage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.familhas.category.CategoryEntity;
import br.com.familhas.city.CityEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.common.Currencies;
import br.com.familhas.enums.EntityStatus;
import br.com.familhas.flight.FlightEntity;
import br.com.familhas.rating.RatingEntity;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "travel_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelPackageEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal pricePromotion;

    /** ISO 4217, vale para price e pricePromotion. */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = Currencies.DEFAULT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntityStatus status;

    @Column(length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private FlightEntity flight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_city_id", nullable = false)
    private CityEntity origin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_city_id", nullable = false)
    private CityEntity destination;

    @OneToMany(mappedBy = "travelPackage")
    @Builder.Default
    private List<RatingEntity> ratings = new ArrayList<>();

    /**
     * Hotel e carro opcionais. Ciclo de vida preso ao pacote: removidos junto com ele.
     * Substituir sempre com clear() + add(), nunca com setComponents (orphanRemoval).
     */
    @OneToMany(mappedBy = "travelPackage", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TravelPackageComponentEntity> components = new ArrayList<>();
}
