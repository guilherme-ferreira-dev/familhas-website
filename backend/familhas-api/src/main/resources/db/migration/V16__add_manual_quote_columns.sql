-- V16: cotação manual feita pelo admin (guia, Fase 10; ADR 0011).
--
-- origin: CUSTOMER (Loja, desde a Fase 5) ou ADMIN (montada no Admin para um cliente, por exemplo depois de um
-- atendimento pelo WhatsApp). O cliente vê e paga as duas do mesmo jeito.
-- manual_discount_amount: desconto em valor dado pelo admin. Já está somado em discount_amount e descontado de
-- total_amount; fica separado só para o Admin saber quanto foi promoção e quanto foi negociação.
-- internal_note: anotação do admin; nunca aparece para o cliente.
-- created_by: username do admin que montou a cotação.

ALTER TABLE quotes ADD (
    origin                 VARCHAR2(20 CHAR)  DEFAULT 'CUSTOMER' NOT NULL,
    manual_discount_amount NUMBER(12, 2)      DEFAULT 0          NOT NULL,
    internal_note          VARCHAR2(500 CHAR),
    created_by             VARCHAR2(180 CHAR)
);

ALTER TABLE quotes ADD CONSTRAINT ck_quotes_origin CHECK (origin IN ('CUSTOMER', 'ADMIN'));
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_manual_discount CHECK (manual_discount_amount >= 0);

-- Lista de cotações do Admin, filtrada por origem, mais recentes primeiro.
CREATE INDEX ix_quotes_origin_created ON quotes (origin, created_at);
