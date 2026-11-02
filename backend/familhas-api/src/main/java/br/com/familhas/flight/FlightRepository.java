package br.com.familhas.flight;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FlightRepository extends JpaRepository<FlightEntity, UUID>, JpaSpecificationExecutor<FlightEntity> {
}
