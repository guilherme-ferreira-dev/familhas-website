-- V12: pagamento, parcelas e idempotência dos webhooks do Stripe (guia, Fase 6; ADR 0007 e 0008).
--
-- Um pagamento por tentativa de checkout (uma Checkout Session). A reserva pode ter vários: falhou, expirou ou
-- trocou de método, abre-se outro. Dados de cartão nunca chegam aqui: só ids do Stripe.
-- idempotency_key vai em toda chamada que escreve no Stripe para esta tentativa.
-- installments é sempre 1 até o parcelamento ser validado na conta Stripe (ADR 0008).

CREATE TABLE payments (
    id                         RAW(16)                      NOT NULL,
    booking_id                 RAW(16)                      NOT NULL,
    method                     VARCHAR2(20 CHAR)            NOT NULL,
    status                     VARCHAR2(20 CHAR)            NOT NULL,
    installments               NUMBER(2)    DEFAULT 1       NOT NULL,
    amount                     NUMBER(12, 2)                NOT NULL,
    currency                   VARCHAR2(3 CHAR)             NOT NULL,
    refunded_amount            NUMBER(12, 2) DEFAULT 0      NOT NULL,
    idempotency_key            VARCHAR2(100 CHAR)           NOT NULL,
    stripe_checkout_session_id VARCHAR2(255 CHAR),
    stripe_payment_intent_id   VARCHAR2(255 CHAR),
    checkout_url               VARCHAR2(2000 CHAR),
    session_expires_at         TIMESTAMP(6) WITH TIME ZONE,
    paid_at                    TIMESTAMP(6) WITH TIME ZONE,
    failure_code               VARCHAR2(100 CHAR),
    created_at                 TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                 TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_payments_checkout_session UNIQUE (stripe_checkout_session_id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_payments_method CHECK (method IN ('PIX', 'CREDIT_CARD')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELED',
        'REFUNDED', 'PARTIALLY_REFUNDED')),
    CONSTRAINT ck_payments_installments CHECK (installments BETWEEN 1 AND 24),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_refunded CHECK (refunded_amount BETWEEN 0 AND amount),
    CONSTRAINT ck_payments_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$'))
);

CREATE INDEX ix_payments_booking ON payments (booking_id, created_at);
-- Webhooks de payment_intent.* e charge.refunded localizam o pagamento pelo PaymentIntent.
CREATE INDEX ix_payments_payment_intent ON payments (stripe_payment_intent_id);
-- Reconciliação: pagamentos PENDING/PROCESSING parados.
CREATE INDEX ix_payments_status_updated ON payments (status, updated_at);

CREATE TABLE installments (
    id                 RAW(16)                      NOT NULL,
    payment_id         RAW(16)                      NOT NULL,
    installment_number NUMBER(2)                    NOT NULL,
    amount             NUMBER(12, 2)                NOT NULL,
    currency           VARCHAR2(3 CHAR)             NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_installments PRIMARY KEY (id),
    CONSTRAINT uq_installments_number UNIQUE (payment_id, installment_number),
    CONSTRAINT fk_installments_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE,
    CONSTRAINT ck_installments_number CHECK (installment_number >= 1),
    CONSTRAINT ck_installments_amount CHECK (amount > 0),
    CONSTRAINT ck_installments_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$'))
);

-- Idempotência por event.id: a linha é gravada na mesma transação da mudança de domínio.
-- Nada do payload do evento é guardado (LGPD e PCI).
CREATE TABLE stripe_webhook_events (
    event_id    VARCHAR2(255 CHAR)           NOT NULL,
    event_type  VARCHAR2(100 CHAR)           NOT NULL,
    outcome     VARCHAR2(20 CHAR)            NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_stripe_webhook_events PRIMARY KEY (event_id),
    CONSTRAINT ck_stripe_webhook_events_outcome CHECK (outcome IN ('PROCESSED', 'IGNORED'))
);
