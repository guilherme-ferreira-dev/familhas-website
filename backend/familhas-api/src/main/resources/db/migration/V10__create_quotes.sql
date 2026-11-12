-- V10: cotação com validade (guia, Fase 5; ADR 0006).
--
-- A cotação guarda um snapshot completo do preço no momento em que foi calculada: custo de cada item,
-- margem, promoção, taxa e total. Mudanças de preço depois disso não alteram a cotação.
-- Status: OPEN até expires_at; depois EXPIRED. A conversão em reserva (Booking) entra na Fase 6.

CREATE TABLE quotes (
    id                 RAW(16)                      NOT NULL,
    client_id          RAW(16)                      NOT NULL,
    travel_package_id  RAW(16),
    status             VARCHAR2(20 CHAR)            NOT NULL,
    passengers         NUMBER(10)                   NOT NULL,
    currency           VARCHAR2(3 CHAR)             NOT NULL,
    components_total   NUMBER(12, 2)                NOT NULL,
    margin_percent     NUMBER(7, 4)                 NOT NULL,
    margin_amount      NUMBER(12, 2)                NOT NULL,
    promotion_percent  NUMBER(7, 4)                 NOT NULL,
    discount_amount    NUMBER(12, 2)                NOT NULL,
    service_fee_amount NUMBER(12, 2)                NOT NULL,
    total_amount       NUMBER(12, 2)                NOT NULL,
    expires_at         TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_quotes PRIMARY KEY (id),
    CONSTRAINT fk_quotes_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_quotes_package FOREIGN KEY (travel_package_id) REFERENCES travel_packages (id),
    CONSTRAINT ck_quotes_status CHECK (status IN ('OPEN', 'EXPIRED')),
    CONSTRAINT ck_quotes_passengers CHECK (passengers BETWEEN 1 AND 9),
    CONSTRAINT ck_quotes_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$')),
    CONSTRAINT ck_quotes_total CHECK (total_amount >= 0)
);

-- "Minhas cotações": por cliente, mais recentes primeiro.
CREATE INDEX ix_quotes_client_created ON quotes (client_id, created_at);
CREATE INDEX ix_quotes_package ON quotes (travel_package_id);

CREATE TABLE quote_items (
    id           RAW(16)                      NOT NULL,
    quote_id     RAW(16)                      NOT NULL,
    position     NUMBER(10)                   NOT NULL,
    item_type    VARCHAR2(20 CHAR)            NOT NULL,
    provider     VARCHAR2(40 CHAR)            NOT NULL,
    offer_id     VARCHAR2(500 CHAR)           NOT NULL,
    description  VARCHAR2(300 CHAR)           NOT NULL,
    start_date   DATE                         NOT NULL,
    end_date     DATE                         NOT NULL,
    unit_price   NUMBER(12, 2)                NOT NULL,
    currency     VARCHAR2(3 CHAR)             NOT NULL,
    quantity     NUMBER(10)                   NOT NULL,
    subtotal     NUMBER(12, 2)                NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_quote_items PRIMARY KEY (id),
    CONSTRAINT fk_quote_items_quote FOREIGN KEY (quote_id) REFERENCES quotes (id) ON DELETE CASCADE,
    CONSTRAINT ck_quote_items_type CHECK (item_type IN ('FLIGHT', 'HOTEL_STAY', 'CAR_RENTAL')),
    CONSTRAINT ck_quote_items_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_quote_items_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_quote_items_quote ON quote_items (quote_id);
