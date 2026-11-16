-- V24: registro de incidentes de segurança e privacidade (LGPD; prompt mestre, Etapa 6; ADR 0018).
--
-- Plano de resposta (docs/incident-response.md): identificar, conter, registrar, avaliar o risco, remediar e, se for
-- o caso, comunicar a ANPD e os titulares. Esta tabela é o registro: datas de cada fase, se houve dado pessoal, o
-- que foi afetado e o que foi feito. Não guarda os dados pessoais em si (só categorias e estimativa).

CREATE TABLE crm_incidents (
    id                   RAW(16)                      NOT NULL,
    title                VARCHAR2(150 CHAR)           NOT NULL,
    description          VARCHAR2(4000 CHAR)          NOT NULL,
    severity             VARCHAR2(10 CHAR)            NOT NULL,
    status               VARCHAR2(10 CHAR)            NOT NULL,
    personal_data        BOOLEAN                      DEFAULT FALSE NOT NULL,
    data_categories      VARCHAR2(300 CHAR),
    affected_estimate    NUMBER(10),
    actions_taken        VARCHAR2(4000 CHAR),
    detected_at          TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    contained_at         TIMESTAMP(6) WITH TIME ZONE,
    closed_at            TIMESTAMP(6) WITH TIME ZONE,
    anpd_notified_at     TIMESTAMP(6) WITH TIME ZONE,
    holders_notified_at  TIMESTAMP(6) WITH TIME ZONE,
    created_by           VARCHAR2(180 CHAR)           NOT NULL,
    version              NUMBER(10)                   DEFAULT 0 NOT NULL,
    created_at           TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_crm_incidents PRIMARY KEY (id),
    CONSTRAINT ck_crm_incidents_severity CHECK (severity IN ('BAIXA', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_crm_incidents_status CHECK (status IN ('ABERTO', 'CONTIDO', 'ENCERRADO'))
);

CREATE INDEX ix_crm_incidents_detected ON crm_incidents (detected_at);
