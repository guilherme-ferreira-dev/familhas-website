-- V14: log de auditoria das ações do admin (guia, Fase 10; ADR 0011).
--
-- Só inserção: ninguém altera nem apaga uma linha pela API. Cada escrita em /api/admin/** (POST, PUT, PATCH,
-- DELETE) e cada exportação vira uma linha, com o status HTTP da resposta (inclusive 4xx).
-- Nunca guarda o corpo da requisição (LGPD): só quem fez, o quê, em qual recurso e quando.
-- resource_type: primeiro segmento depois de /api/admin (ex.: travel-packages). "resource" é palavra reservada no Oracle.
-- details: só texto montado pelo código (ex.: filtros de uma exportação), nunca dado vindo do cliente final.

CREATE TABLE audit_logs (
    id             RAW(16)                      NOT NULL,
    actor_subject  VARCHAR2(64 CHAR)            NOT NULL,
    actor_username VARCHAR2(180 CHAR),
    action         VARCHAR2(20 CHAR)            NOT NULL,
    resource_type  VARCHAR2(60 CHAR)            NOT NULL,
    resource_id    VARCHAR2(64 CHAR),
    http_method    VARCHAR2(10 CHAR)            NOT NULL,
    path           VARCHAR2(300 CHAR)           NOT NULL,
    status         NUMBER(3)                    NOT NULL,
    details        VARCHAR2(1000 CHAR),
    occurred_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT ck_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'EXPORT'))
);

-- Tela de auditoria: mais recentes primeiro, com filtro por recurso e por quem fez.
CREATE INDEX ix_audit_logs_occurred ON audit_logs (occurred_at);
CREATE INDEX ix_audit_logs_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_subject, occurred_at);
