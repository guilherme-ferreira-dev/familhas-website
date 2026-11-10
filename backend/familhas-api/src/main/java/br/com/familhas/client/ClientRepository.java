package br.com.familhas.client;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClientRepository extends JpaRepository<ClientEntity, UUID>, JpaSpecificationExecutor<ClientEntity> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    Optional<ClientEntity> findByKeycloakId(String keycloakId);

    Optional<ClientEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNotAndKeycloakIdIsNotNull(String email, UUID id);
}
