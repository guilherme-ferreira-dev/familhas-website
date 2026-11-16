package br.com.familhas.crm.brand;

import java.util.UUID;

import br.com.familhas.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Identidade visual da agência (V22). Linha única (id = 1). */
@Entity
@Table(name = "crm_brand")
@Getter
@Setter
@NoArgsConstructor
public class CrmBrandEntity extends AuditableEntity {

    public static final int SINGLE_ID = 1;

    @Id
    private Integer id = SINGLE_ID;

    @Column(name = "logo_file_id")
    private UUID logoFileId;

    @Column(name = "updated_by", length = 180)
    private String updatedBy;
}
