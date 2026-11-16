package br.com.familhas.crm.backup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmBackupRepository extends JpaRepository<CrmBackupEntity, UUID> {

    List<CrmBackupEntity> findByExpiresAtAfterOrderByCreatedAtDesc(Instant now);

    List<CrmBackupEntity> findByExpiresAtBefore(Instant now);
}
