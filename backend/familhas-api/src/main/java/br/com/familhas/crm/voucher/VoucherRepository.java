package br.com.familhas.crm.voucher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository extends JpaRepository<VoucherEntity, UUID>, JpaSpecificationExecutor<VoucherEntity> {

    @Query(value = "select seq_voucher_number.nextval from dual", nativeQuery = true)
    long nextNumber();

    /** Emitidos (não cancelados) no período, por tipo: [tipo, quantidade]. */
    @Query("""
            select v.type, count(v) from VoucherEntity v
            where v.status = br.com.familhas.crm.voucher.VoucherStatus.ISSUED and v.issuedAt >= :from and v.issuedAt < :to
            group by v.type
            """)
    List<Object[]> countIssuedByType(@Param("from") Instant from, @Param("to") Instant to);
}
