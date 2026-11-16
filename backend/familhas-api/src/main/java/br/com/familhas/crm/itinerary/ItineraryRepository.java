package br.com.familhas.crm.itinerary;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, UUID> {

    @Query("""
            select i from ItineraryEntity i
            where :q is null or lower(i.destination) like :q escape '\\' or lower(i.clientName) like :q escape '\\'
            """)
    Page<ItineraryEntity> search(@Param("q") String likePattern, Pageable pageable);
}
