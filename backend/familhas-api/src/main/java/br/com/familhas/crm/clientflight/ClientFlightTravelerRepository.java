package br.com.familhas.crm.clientflight;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientFlightTravelerRepository extends JpaRepository<ClientFlightTravelerEntity, UUID> {

    /** Passageiros com o CPF (LGPD: pedidos de titular). */
    List<ClientFlightTravelerEntity> findByCpf(String cpf);
}
