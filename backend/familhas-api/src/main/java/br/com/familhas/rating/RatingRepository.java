package br.com.familhas.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<RatingEntity, UUID>, JpaSpecificationExecutor<RatingEntity> {

    boolean existsByClient_IdAndTravelPackage_Id(UUID clientId, UUID travelPackageId);

    boolean existsByClient_IdAndTravelPackage_IdAndIdNot(UUID clientId, UUID travelPackageId, UUID id);

    List<RatingEntity> findByClient_Id(UUID clientId);

    /** Avaliações do cliente com o pacote (categoria, destino e voo vêm pelo batch fetch). */
    @EntityGraph(attributePaths = "travelPackage")
    Page<RatingEntity> findByClient_Id(UUID clientId, Pageable pageable);

    /** Média e quantidade por pacote, para a recomendação. */
    @Query("select r.travelPackage.id as travelPackageId, avg(r.rating) as average, count(r) as total"
            + " from RatingEntity r group by r.travelPackage.id")
    List<PackageRatingStats> findPackageStats();

    interface PackageRatingStats {
        UUID getTravelPackageId();

        Double getAverage();

        Long getTotal();
    }
}
