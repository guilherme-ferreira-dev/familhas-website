package br.com.familhas.hotel;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HotelRepository extends JpaRepository<HotelEntity, UUID>, JpaSpecificationExecutor<HotelEntity> {
}
