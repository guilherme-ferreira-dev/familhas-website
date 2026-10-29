package br.com.familhas.carRental;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CarRentalOfferRepository
        extends JpaRepository<CarRentalOfferEntity, UUID>, JpaSpecificationExecutor<CarRentalOfferEntity> {
}
