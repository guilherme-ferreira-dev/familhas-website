package br.com.familhas.crm.flightsearch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlightSearchRepository extends JpaRepository<FlightSearchEntity, UUID> {

    Optional<FlightSearchEntity> findByParamsHash(String paramsHash);

    List<FlightSearchEntity> findTop5ByOrderByLastRunAtDesc();
}
