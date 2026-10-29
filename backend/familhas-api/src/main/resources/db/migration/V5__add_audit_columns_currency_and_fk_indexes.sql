-- V5: colunas de auditoria, moeda explícita nos pacotes e índices de chaves estrangeiras.
-- Ver docs/adr/0003-catalogo-paginacao-auditoria.md.
--
-- created_at/updated_at: TIMESTAMP WITH TIME ZONE (Instant na JVM, UTC).
-- O DEFAULT preenche as linhas já existentes; nas novas o Hibernate grava os valores.

ALTER TABLE categories ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE cities ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE flights ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE clients ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE travel_packages ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE ratings ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

-- Tabelas de junção (@ManyToMany) não são entidades: só created_at, preenchido pelo banco.
ALTER TABLE client_favorites ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

ALTER TABLE client_flights ADD (
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL);

-- Moeda ISO 4217 explícita (guia, seção 6 item 5). Os pacotes existentes estão em reais.
ALTER TABLE travel_packages ADD (
    currency VARCHAR2(3 CHAR) DEFAULT 'BRL' NOT NULL);

ALTER TABLE travel_packages ADD CONSTRAINT ck_tp_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$'));

-- O Oracle não cria índice automático em FK; estes cobrem os filtros de busca e os joins.
CREATE INDEX ix_tp_origin_city ON travel_packages (origin_city_id);
CREATE INDEX ix_tp_destination_city ON travel_packages (destination_city_id);
CREATE INDEX ix_tp_category ON travel_packages (category_id);
CREATE INDEX ix_tp_flight ON travel_packages (flight_id);
CREATE INDEX ix_ratings_package ON ratings (travel_package_id);
CREATE INDEX ix_client_favorites_package ON client_favorites (travel_package_id);
CREATE INDEX ix_client_flights_package ON client_flights (travel_package_id);
