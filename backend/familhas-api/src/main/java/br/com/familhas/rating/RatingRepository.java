package br.com.familhas.rating;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RatingRepository extends JpaRepository<RatingEntity, UUID>, JpaSpecificationExecutor<RatingEntity> {

    boolean existsByClient_IdAndTravelPackage_Id(UUID clientId, UUID travelPackageId);

    boolean existsByClient_IdAndTravelPackage_IdAndIdNot(UUID clientId, UUID travelPackageId, UUID id);
}
