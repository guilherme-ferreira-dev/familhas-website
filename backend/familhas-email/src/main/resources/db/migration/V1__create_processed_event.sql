-- V1 (familhas-email): idempotência dos consumidores (ADR 0005).
--
-- Uma linha por (evento, consumidor). A linha é inserida na mesma transação do processamento,
-- antes do envio: se o envio falhar, o rollback libera o evento para nova tentativa; se a mesma
-- mensagem chegar de novo depois do commit, a chave primária impede o segundo envio.
-- Nenhum dado pessoal é guardado aqui.

CREATE TABLE processed_event (
    event_id      RAW(16)                      NOT NULL,
    consumer      VARCHAR2(100 CHAR)           NOT NULL,
    event_type    VARCHAR2(100 CHAR)           NOT NULL,
    processed_at  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id, consumer)
);

-- Limpeza futura por idade (retenção documentada no ADR 0005).
CREATE INDEX ix_processed_event_processed_at ON processed_event (processed_at);
