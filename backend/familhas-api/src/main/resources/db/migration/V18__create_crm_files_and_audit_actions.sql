-- V18: arquivos privados do CRM interno e novas ações de auditoria (prompt mestre do CRM, Etapa 2; ADR 0014).
--
-- crm_files guarda só os metadados. O conteúdo fica no storage privado (volume local por enquanto), sob uma
-- chave aleatória (storage_key) que nunca deriva do nome enviado pelo usuário. original_name é só exibição.
-- category decide qual permissão do CRM vale para o arquivo (ex.: DOCUMENT -> crm:documentos:*).
-- sha256 permite conferir integridade e achar duplicados; nunca é usado como caminho.

CREATE TABLE crm_files (
    id                RAW(16)                      NOT NULL,
    category          VARCHAR2(30 CHAR)            NOT NULL,
    storage_key       VARCHAR2(80 CHAR)            NOT NULL,
    original_name     VARCHAR2(200 CHAR)           NOT NULL,
    content_type      VARCHAR2(60 CHAR)            NOT NULL,
    size_bytes        NUMBER(12)                   NOT NULL,
    sha256            VARCHAR2(64 CHAR)            NOT NULL,
    uploaded_by       VARCHAR2(64 CHAR)            NOT NULL,
    uploaded_by_name  VARCHAR2(180 CHAR),
    created_at        TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_crm_files PRIMARY KEY (id),
    CONSTRAINT uq_crm_files_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_crm_files_category CHECK (category IN
        ('DOCUMENT', 'FLIGHT_ATTACHMENT', 'VOUCHER_SOURCE', 'POST_IMAGE', 'INSTAGRAM_MEDIA', 'ITINERARY_PHOTO')),
    CONSTRAINT ck_crm_files_content_type CHECK (content_type IN
        ('application/pdf', 'image/jpeg', 'image/png', 'image/webp', 'video/mp4')),
    CONSTRAINT ck_crm_files_size CHECK (size_bytes > 0)
);

-- Busca por nome dentro da categoria (Documentos) e listagem dos mais recentes.
CREATE INDEX ix_crm_files_category_created ON crm_files (category, created_at);
CREATE INDEX ix_crm_files_category_name ON crm_files (category, LOWER(original_name));

-- Auditoria: link de download de arquivo sensível, publicação (Meta), restauração e importação de dados.
ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_action;
ALTER TABLE audit_logs ADD CONSTRAINT ck_audit_logs_action CHECK (action IN
    ('CREATE', 'UPDATE', 'DELETE', 'EXPORT', 'DOWNLOAD', 'PUBLISH', 'RESTORE', 'IMPORT'));
