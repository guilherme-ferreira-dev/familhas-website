-- V20: Pesquisar Voos (prompt mestre, Etapa 3; ADR 0015).
--
-- Grupos de destinos da agência (até 7 destinos por grupo, códigos IATA separados por vírgula) e o histórico das
-- pesquisas. Pesquisa repetida (mesmos parâmetros, mesmo params_hash) só renova last_run_at: a tela mostra as 5
-- mais recentes com o botão "Refazer". Resultados não são guardados: preço de passagem muda a todo momento.

CREATE TABLE flight_search_groups (
    id           RAW(16)                      NOT NULL,
    name         VARCHAR2(60 CHAR)            NOT NULL,
    destinations VARCHAR2(40 CHAR)            NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_flight_search_groups PRIMARY KEY (id),
    CONSTRAINT uq_flight_search_groups_name UNIQUE (name),
    CONSTRAINT ck_flight_search_groups_dest CHECK (REGEXP_LIKE(destinations, '^[A-Z]{3}(,[A-Z]{3}){0,6}$'))
);

CREATE TABLE flight_searches (
    id           RAW(16)                      NOT NULL,
    params_hash  VARCHAR2(64 CHAR)            NOT NULL,
    params       VARCHAR2(1000 CHAR)          NOT NULL,
    summary      VARCHAR2(300 CHAR)           NOT NULL,
    run_by       VARCHAR2(180 CHAR),
    last_run_at  TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_flight_searches PRIMARY KEY (id),
    CONSTRAINT uq_flight_searches_hash UNIQUE (params_hash),
    CONSTRAINT ck_flight_searches_params_json CHECK (params IS JSON)
);

CREATE INDEX ix_flight_searches_last_run ON flight_searches (last_run_at);
