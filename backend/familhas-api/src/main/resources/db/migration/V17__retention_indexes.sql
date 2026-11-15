-- V17: índices da limpeza por idade (guia, Fase 11; ADR 0012).
--
-- O job de retenção apaga em lotes:
--   outbox_event          PUBLISHED com published_at mais antigo que o prazo (FAILED fica para ação manual)
--   stripe_webhook_events registros de idempotência com created_at mais antigo que o prazo
--   quotes                cotações vencidas (expires_at) há mais que o prazo e sem reserva; os itens vão
--                         junto pelo ON DELETE CASCADE de quote_items
-- Reservas e pagamentos não são apagados: são registro financeiro (ADR 0012).

CREATE INDEX ix_outbox_event_status_published ON outbox_event (status, published_at);

CREATE INDEX ix_stripe_webhook_events_created ON stripe_webhook_events (created_at);

CREATE INDEX ix_quotes_expires ON quotes (expires_at);
