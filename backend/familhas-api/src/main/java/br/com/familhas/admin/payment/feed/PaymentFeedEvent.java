package br.com.familhas.admin.payment.feed;

import java.time.Instant;
import java.util.UUID;

/** Aviso de mudança de pagamento para a planilha. Só ids: os dados vêm de GET /api/admin/payments/{id}. */
public record PaymentFeedEvent(String eventType, UUID paymentId, UUID bookingId, Instant occurredAt) {
}
