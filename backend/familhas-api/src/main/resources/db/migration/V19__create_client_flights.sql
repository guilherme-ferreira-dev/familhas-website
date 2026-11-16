-- V19: Voos Clientes do CRM interno (prompt mestre, Etapa 3; ADR 0015). Nomes do JSON: voo_flights e voo_travelers.
--
-- Um voo da agência (ida e, se houver, volta) com os passageiros. A lista ordena pela ida (ix_voo_flights_outbound).
-- name_search guarda o nome sem acento e em minúsculas, para a busca rápida "em qualquer ordem" (cada palavra
-- digitada precisa estar no nome do mesmo passageiro). O CPF é opcional e nunca sai inteiro da API (LGPD).
-- version: trava otimista (duas pessoas editando o mesmo voo: a segunda recebe 409).

CREATE TABLE voo_flights (
    id                     RAW(16)                      NOT NULL,
    locator                VARCHAR2(20 CHAR),
    airline                VARCHAR2(60 CHAR)            NOT NULL,
    origin                 VARCHAR2(60 CHAR)            NOT NULL,
    destination            VARCHAR2(60 CHAR)            NOT NULL,
    outbound_at            TIMESTAMP(6)                 NOT NULL,
    outbound_flight_number VARCHAR2(20 CHAR),
    return_at              TIMESTAMP(6),
    return_flight_number   VARCHAR2(20 CHAR),
    notes                  VARCHAR2(2000 CHAR),
    archived_at            TIMESTAMP(6) WITH TIME ZONE,
    created_by             VARCHAR2(180 CHAR),
    version                NUMBER(10)                   DEFAULT 0 NOT NULL,
    created_at             TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at             TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_voo_flights PRIMARY KEY (id),
    CONSTRAINT ck_voo_flights_return CHECK (return_at IS NULL OR return_at >= outbound_at)
);

-- outbound_at é horário local do voo (sem fuso), como aparece no bilhete.
CREATE INDEX ix_voo_flights_outbound ON voo_flights (archived_at, outbound_at);
CREATE INDEX ix_voo_flights_locator ON voo_flights (UPPER(locator));

CREATE TABLE voo_travelers (
    id          RAW(16)             NOT NULL,
    flight_id   RAW(16)             NOT NULL,
    sort_order  NUMBER(3)           NOT NULL,
    full_name   VARCHAR2(150 CHAR)  NOT NULL,
    name_search VARCHAR2(150 CHAR)  NOT NULL,
    cpf         VARCHAR2(11 CHAR),
    phone       VARCHAR2(20 CHAR),
    CONSTRAINT pk_voo_travelers PRIMARY KEY (id),
    CONSTRAINT fk_voo_travelers_flight FOREIGN KEY (flight_id) REFERENCES voo_flights (id) ON DELETE CASCADE,
    CONSTRAINT ck_voo_travelers_cpf CHECK (cpf IS NULL OR REGEXP_LIKE(cpf, '^[0-9]{11}$'))
);

CREATE INDEX ix_voo_travelers_flight ON voo_travelers (flight_id, sort_order);
CREATE INDEX ix_voo_travelers_name ON voo_travelers (name_search);

-- Anexos do voo: arquivos privados da categoria FLIGHT_ATTACHMENT (V18).
CREATE TABLE voo_flight_attachments (
    flight_id RAW(16) NOT NULL,
    file_id   RAW(16) NOT NULL,
    CONSTRAINT pk_voo_flight_attachments PRIMARY KEY (flight_id, file_id),
    CONSTRAINT fk_voo_attach_flight FOREIGN KEY (flight_id) REFERENCES voo_flights (id) ON DELETE CASCADE,
    CONSTRAINT fk_voo_attach_file FOREIGN KEY (file_id) REFERENCES crm_files (id) ON DELETE CASCADE
);

CREATE INDEX ix_voo_flight_attachments_file ON voo_flight_attachments (file_id);
