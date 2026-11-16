-- V21: Emitir Voucher (prompt mestre, Etapa 3; ADR 0015).
--
-- Voucher de voo, hotel, transfer ou passeio. NOME, CPF, TELEFONE e OBSERVAÇÕES são sempre digitados pela equipe
-- (nunca extraídos por IA). details guarda os campos do serviço em JSON (validados pela API por tipo).
-- number: sequencial legível (VF-000001), único. source_file_id: PDF/imagem de origem (categoria VOUCHER_SOURCE).
-- Cancelar não apaga: o histórico de emitidos fica.

CREATE SEQUENCE seq_voucher_number START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE vouchers (
    id              RAW(16)                      NOT NULL,
    voucher_number  VARCHAR2(20 CHAR)            NOT NULL,
    voucher_type    VARCHAR2(20 CHAR)            NOT NULL,
    status          VARCHAR2(20 CHAR)            NOT NULL,
    client_name     VARCHAR2(150 CHAR)           NOT NULL,
    client_cpf      VARCHAR2(11 CHAR),
    client_phone    VARCHAR2(20 CHAR),
    notes           VARCHAR2(2000 CHAR),
    details         CLOB                         NOT NULL,
    service_date    DATE,
    source_file_id  RAW(16),
    issued_by       VARCHAR2(180 CHAR),
    issued_at       TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    cancelled_at    TIMESTAMP(6) WITH TIME ZONE,
    version         NUMBER(10)                   DEFAULT 0 NOT NULL,
    created_at      TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_vouchers PRIMARY KEY (id),
    CONSTRAINT uq_vouchers_number UNIQUE (voucher_number),
    CONSTRAINT ck_vouchers_type CHECK (voucher_type IN ('FLIGHT', 'HOTEL', 'TRANSFER', 'TOUR')),
    CONSTRAINT ck_vouchers_status CHECK (status IN ('ISSUED', 'CANCELLED')),
    CONSTRAINT ck_vouchers_cpf CHECK (client_cpf IS NULL OR REGEXP_LIKE(client_cpf, '^[0-9]{11}$')),
    CONSTRAINT ck_vouchers_details_json CHECK (details IS JSON),
    CONSTRAINT fk_vouchers_source_file FOREIGN KEY (source_file_id) REFERENCES crm_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_vouchers_issued ON vouchers (issued_at);
CREATE INDEX ix_vouchers_type_issued ON vouchers (voucher_type, issued_at);
CREATE INDEX ix_vouchers_client ON vouchers (LOWER(client_name));
CREATE INDEX ix_vouchers_source_file ON vouchers (source_file_id);
