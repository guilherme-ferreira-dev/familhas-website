package br.com.familhas.hotel;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomOfferRepository
        extends JpaRepository<RoomOfferEntity, UUID>, JpaSpecificationExecutor<RoomOfferEntity> {
}
