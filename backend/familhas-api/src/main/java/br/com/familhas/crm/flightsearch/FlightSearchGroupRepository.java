package br.com.familhas.crm.flightsearch;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightSearchGroupRepository extends JpaRepository<FlightSearchGroupEntity, UUID> {

    List<FlightSearchGroupEntity> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByNameIgnoreCase(String name);
}
