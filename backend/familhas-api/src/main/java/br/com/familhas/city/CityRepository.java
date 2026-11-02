package br.com.familhas.city;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CityRepository extends JpaRepository<CityEntity, UUID>, JpaSpecificationExecutor<CityEntity> {
}
