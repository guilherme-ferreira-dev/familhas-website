-- V13: índice para "Minhas viagens" (Fase 8; ADR 0010).
--
-- As viagens são reservas CONFIRMED ou COMPLETED do cliente: a leitura filtra por client_id e status.
-- O mesmo índice atende as abas de clientes do Admin (Fase 10), que filtram reservas por cliente e status.

CREATE INDEX ix_bookings_client_status ON bookings (client_id, status);
