package br.com.familhas.travelPackage;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TravelPackageRepository
        extends JpaRepository<TravelPackageEntity, UUID>, JpaSpecificationExecutor<TravelPackageEntity> {
}