package br.com.familhas.crm.file;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CrmFileRepository extends JpaRepository<CrmFileEntity, UUID> {

    /** Busca por nome sem diferenciar maiúsculas (índice em LOWER(original_name)). Parâmetro sempre bind. */
    @Query("""
            select f from CrmFileEntity f
            where f.category = :category
              and (:name is null or lower(f.originalName) like concat('%', :name, '%'))
            """)
    Page<CrmFileEntity> search(@Param("category") FileCategory category, @Param("name") String name,
            Pageable pageable);
}
