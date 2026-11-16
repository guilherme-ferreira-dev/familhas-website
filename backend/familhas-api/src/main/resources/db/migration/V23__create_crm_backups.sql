-- V23: backups lógicos do CRM guardados para o envio por e-mail (prompt mestre, Etapa 5; ADR 0017).
--
-- O arquivo fica no storage privado criptografado (AES-256-GCM com BACKUP_ENCRYPTION_KEY) e vence em expires_at.
-- O e-mail leva só um link para a tela de backup do CRM (exige login), nunca o arquivo: ele tem CPFs.
-- O download direto (sem guardar) não passa por esta tabela.

CREATE TABLE crm_backups (
    id            RAW(16)                      NOT NULL,
    storage_key   VARCHAR2(80 CHAR)            NOT NULL,
    size_bytes    NUMBER(12)                   NOT NULL,
    sha256        VARCHAR2(64 CHAR)            NOT NULL,
    record_count  NUMBER(10)                   NOT NULL,
    created_by    VARCHAR2(180 CHAR)           NOT NULL,
    emailed_to    VARCHAR2(180 CHAR),
    expires_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_crm_backups PRIMARY KEY (id),
    CONSTRAINT uq_crm_backups_storage_key UNIQUE (storage_key)
);

CREATE INDEX ix_crm_backups_expires ON crm_backups (expires_at);
