package br.com.familhas.payment.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeWebhookEventRepository extends JpaRepository<StripeWebhookEventEntity, String> {
}
