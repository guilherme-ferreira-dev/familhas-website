package br.com.familhas.admin.payment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.familhas.payment.PaymentEntity;

/** Consultas do Admin sobre pagamentos (filtros dinâmicos). As escritas continuam só no PaymentRepository. */
public interface AdminPaymentRepository extends JpaRepository<PaymentEntity, UUID>,
        JpaSpecificationExecutor<PaymentEntity> {
}
