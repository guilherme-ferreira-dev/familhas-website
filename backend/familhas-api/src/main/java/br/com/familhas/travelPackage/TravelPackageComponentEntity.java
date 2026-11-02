package br.com.familhas.travelPackage;

import java.util.UUID;

import br.com.familhas.carRental.CarRentalOfferEntity;
import br.com.familhas.common.AuditableEntity;
import br.com.familhas.enums.PackageComponentType;
import br.com.familhas.hotel.RoomOfferEntity;

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

/**
 * Componente opcional de pacote (hotel ou carro). Modelo flexível no lugar de campos fixos
 * no pacote (guia, seção 7). Exatamente uma referência é preenchida, conforme o tipo.
 */
@Entity
@Table(name = "travel_package_components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelPackageComponentEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_package_id", nullable = false)
    private TravelPackageEntity travelPackage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackageComponentType componentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_offer_id")
    private RoomOfferEntity roomOffer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_rental_offer_id")
    private CarRentalOfferEntity carRentalOffer;

    /** Noites (HOTEL_STAY) ou dias (CAR_RENTAL). */
    @Column(nullable = false)
    private Integer quantity;
}
