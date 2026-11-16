-- V22: Marketing & Conteúdo do CRM interno (prompt mestre, Etapa 4; ADR 0016).
--
-- crm_brand: a logo oficial (sem fundo azul, sem slogan), enviada uma vez e composta em todos os posts no navegador,
-- nunca desenhada pela IA. Uma linha só (id = 1): é uma agência.
-- instagram_posts / instagram_post_slides: a fila de posts. Publicação pela Meta com claim atômico (status
-- PUBLISHING só sai de READY/FAILED por UPDATE condicional) e sem reenvio depois de publish_attempted_at: daí em
-- diante uma falha vira UNCERTAIN e exige conferência humana no Instagram.
-- itineraries: roteiros (proposta comercial em uma página A4). internal_alerts nunca vai para o cliente.

-- Arquivos: logo e mídia dos posts.
ALTER TABLE crm_files DROP CONSTRAINT ck_crm_files_category;
ALTER TABLE crm_files ADD CONSTRAINT ck_crm_files_category CHECK (category IN
    ('DOCUMENT', 'FLIGHT_ATTACHMENT', 'VOUCHER_SOURCE', 'POST_IMAGE', 'INSTAGRAM_MEDIA', 'ITINERARY_PHOTO',
     'BRAND_ASSET'));

CREATE TABLE crm_brand (
    id            NUMBER(1)                    NOT NULL,
    logo_file_id  RAW(16),
    updated_by    VARCHAR2(180 CHAR),
    created_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_crm_brand PRIMARY KEY (id),
    CONSTRAINT ck_crm_brand_single CHECK (id = 1),
    CONSTRAINT fk_crm_brand_logo FOREIGN KEY (logo_file_id) REFERENCES crm_files (id) ON DELETE SET NULL
);

CREATE TABLE instagram_posts (
    id                    RAW(16)                      NOT NULL,
    post_format           VARCHAR2(20 CHAR)            NOT NULL,
    origin                VARCHAR2(20 CHAR)            NOT NULL,
    status                VARCHAR2(20 CHAR)            NOT NULL,
    title                 VARCHAR2(120 CHAR)           NOT NULL,
    idea                  VARCHAR2(2000 CHAR),
    caption               VARCHAR2(2200 CHAR),
    hashtags              VARCHAR2(500 CHAR),
    video_file_id         RAW(16),
    meta_media_id         VARCHAR2(64 CHAR),
    permalink             VARCHAR2(300 CHAR),
    last_error            VARCHAR2(500 CHAR),
    publish_claimed_at    TIMESTAMP(6) WITH TIME ZONE,
    publish_attempted_at  TIMESTAMP(6) WITH TIME ZONE,
    published_at          TIMESTAMP(6) WITH TIME ZONE,
    published_by          VARCHAR2(180 CHAR),
    created_by            VARCHAR2(180 CHAR),
    version               NUMBER(10)                   DEFAULT 0 NOT NULL,
    created_at            TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at            TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_instagram_posts PRIMARY KEY (id),
    CONSTRAINT ck_instagram_posts_format CHECK (post_format IN ('STORY', 'FEED', 'REEL')),
    CONSTRAINT ck_instagram_posts_origin CHECK (origin IN ('MANUAL', 'QUOTE_POST', 'CRUISE_POST', 'AI')),
    CONSTRAINT ck_instagram_posts_status CHECK (status IN
        ('DRAFT', 'READY', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'UNCERTAIN')),
    CONSTRAINT fk_instagram_posts_video FOREIGN KEY (video_file_id) REFERENCES crm_files (id) ON DELETE SET NULL
);

CREATE INDEX ix_instagram_posts_status ON instagram_posts (status, created_at);
CREATE INDEX ix_instagram_posts_video ON instagram_posts (video_file_id);

-- Até 20 lâminas (carrossel). image_file_id: a imagem final com marca; clean_file_id: a cópia "-limpa", sem textos,
-- guardada para reescrever o texto sem gerar a imagem de novo.
CREATE TABLE instagram_post_slides (
    id             RAW(16)             NOT NULL,
    post_id        RAW(16)             NOT NULL,
    sort_order     NUMBER(2)           NOT NULL,
    image_file_id  RAW(16)             NOT NULL,
    clean_file_id  RAW(16),
    hook           VARCHAR2(120 CHAR),
    title          VARCHAR2(120 CHAR),
    bullets        VARCHAR2(1000 CHAR),
    caption        VARCHAR2(1000 CHAR),
    CONSTRAINT pk_instagram_post_slides PRIMARY KEY (id),
    CONSTRAINT uq_instagram_post_slides_order UNIQUE (post_id, sort_order),
    CONSTRAINT fk_ig_slides_post FOREIGN KEY (post_id) REFERENCES instagram_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_ig_slides_image FOREIGN KEY (image_file_id) REFERENCES crm_files (id),
    CONSTRAINT fk_ig_slides_clean FOREIGN KEY (clean_file_id) REFERENCES crm_files (id) ON DELETE SET NULL,
    CONSTRAINT ck_ig_slides_order CHECK (sort_order BETWEEN 0 AND 19)
);

CREATE INDEX ix_ig_slides_image ON instagram_post_slides (image_file_id);
CREATE INDEX ix_ig_slides_clean ON instagram_post_slides (clean_file_id);

CREATE TABLE itineraries (
    id                RAW(16)                      NOT NULL,
    destination       VARCHAR2(120 CHAR)           NOT NULL,
    days              NUMBER(2)                    NOT NULL,
    client_name       VARCHAR2(150 CHAR),
    client_phone      VARCHAR2(20 CHAR),
    travel_period     VARCHAR2(120 CHAR),
    price_text        VARCHAR2(200 CHAR),
    payment_terms     VARCHAR2(300 CHAR),
    observations      VARCHAR2(2000 CHAR),
    sections          CLOB                         NOT NULL,
    internal_alerts   VARCHAR2(2000 CHAR),
    photo_ids         VARCHAR2(400 CHAR),
    created_by        VARCHAR2(180 CHAR),
    version           NUMBER(10)                   DEFAULT 0 NOT NULL,
    created_at        TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT pk_itineraries PRIMARY KEY (id),
    CONSTRAINT ck_itineraries_days CHECK (days BETWEEN 1 AND 30),
    CONSTRAINT ck_itineraries_sections_json CHECK (sections IS JSON)
);

CREATE INDEX ix_itineraries_created ON itineraries (created_at);
CREATE INDEX ix_itineraries_destination ON itineraries (LOWER(destination));
