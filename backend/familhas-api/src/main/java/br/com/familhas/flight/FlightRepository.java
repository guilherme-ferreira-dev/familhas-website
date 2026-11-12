package br.com.familhas.flight;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlightRepository extends JpaRepository<FlightEntity, UUID>, JpaSpecificationExecutor<FlightEntity> {

    /**
     * Bloqueia assentos para uma reserva (ADR 0007, D3). Update condicional: com reservas simultâneas, só desconta
     * se ainda houver assentos, sem ler antes. Retorna 0 quando não há assentos suficientes ou o voo não está ativo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FlightEntity f set f.seatsAvailable = f.seatsAvailable - :seats
             where f.id = :id and f.status = br.com.familhas.enums.EntityStatus.ACTIVE and f.seatsAvailable >= :seats
            """)
    int holdSeats(@Param("id") UUID id, @Param("seats") int seats);

    /** Devolve os assentos de uma reserva cancelada. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FlightEntity f set f.seatsAvailable = f.seatsAvailable + :seats where f.id = :id")
    int releaseSeats(@Param("id") UUID id, @Param("seats") int seats);
}
