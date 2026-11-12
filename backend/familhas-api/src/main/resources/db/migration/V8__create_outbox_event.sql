-- V8: Transactional Outbox (guia, seção 6 item 6; ADR 0005).
--
-- O evento é gravado na mesma transação da mudança de domínio. O OutboxPublisher lê as linhas
-- PENDING com FOR UPDATE SKIP LOCKED, publica no RabbitMQ com publisher confirms e marca PUBLISHED.
-- O id da linha é o próprio eventId do contrato.

CREATE TABLE outbox_event (
    id              RAW(16)                      NOT NULL,
    aggregate_type  VARCHAR2(60 CHAR)            NOT NULL,
    aggregate_id    VARCHAR2(64 CHAR)            NOT NULL,
    event_type      VARCHAR2(100 CHAR)           NOT NULL,
    routing_key     VARCHAR2(150 CHAR)           NOT NULL,
    schema_version  NUMBER(5)                    NOT NULL,
    payload         CLOB                         NOT NULL,
    correlation_id  VARCHAR2(64 CHAR),
    status          VARCHAR2(20 CHAR)            NOT NULL,
    attempts        NUMBER(5)    DEFAULT 0       NOT NULL,
    last_error      VARCHAR2(500 CHAR),
    available_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    published_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at      TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_event_payload_json CHECK (payload IS JSON)
);

-- Busca do publisher: pendentes já liberados para nova tentativa.
CREATE INDEX ix_outbox_event_status_available ON outbox_event (status, available_at);

-- Rastreio por agregado (ex.: eventos de um cliente).
CREATE INDEX ix_outbox_event_aggregate ON outbox_event (aggregate_type, aggregate_id);
