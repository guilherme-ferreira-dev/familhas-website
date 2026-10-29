-- V6: hotéis, ofertas de quarto, aluguel de carro e componentes flexíveis de pacote.
-- Ver docs/adr/0003-catalogo-paginacao-auditoria.md.
--
-- UUIDs de seed (mesma convenção do V4, prefixo legível por tabela):
--   hotels                    ee1e0000-0000-4000-8000-00000000000N
--   room_offers               ee2e0000-0000-4000-8000-00000000000N
--   car_rental_offers         ca2e0000-0000-4000-8000-00000000000N
--   travel_package_components cc0e0000-0000-4000-8000-00000000000N

CREATE TABLE hotels (
    id RAW(16) NOT NULL,
    name VARCHAR2(180 CHAR) NOT NULL,
    city_id RAW(16) NOT NULL,
    address VARCHAR2(300 CHAR),
    stars NUMBER(10) NOT NULL,
    description VARCHAR2(1000 CHAR),
    image_url VARCHAR2(500 CHAR),
    status VARCHAR2(20 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_hotels PRIMARY KEY (id),
    CONSTRAINT fk_hotels_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_hotels_stars CHECK (stars BETWEEN 1 AND 5)
);
CREATE INDEX ix_hotels_city ON hotels (city_id);

CREATE TABLE room_offers (
    id RAW(16) NOT NULL,
    hotel_id RAW(16) NOT NULL,
    room_type VARCHAR2(120 CHAR) NOT NULL,
    board_type VARCHAR2(20 CHAR) NOT NULL,
    max_guests NUMBER(10) NOT NULL,
    price_per_night NUMBER(12, 2) NOT NULL,
    currency VARCHAR2(3 CHAR) DEFAULT 'BRL' NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_room_offers PRIMARY KEY (id),
    CONSTRAINT fk_room_offers_hotel FOREIGN KEY (hotel_id) REFERENCES hotels (id),
    CONSTRAINT ck_room_offers_guests CHECK (max_guests >= 1),
    CONSTRAINT ck_room_offers_price CHECK (price_per_night > 0),
    CONSTRAINT ck_room_offers_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$'))
);
CREATE INDEX ix_room_offers_hotel ON room_offers (hotel_id);

CREATE TABLE car_rental_offers (
    id RAW(16) NOT NULL,
    city_id RAW(16) NOT NULL,
    company_name VARCHAR2(120 CHAR) NOT NULL,
    car_model VARCHAR2(120 CHAR) NOT NULL,
    car_category VARCHAR2(20 CHAR) NOT NULL,
    transmission VARCHAR2(20 CHAR) NOT NULL,
    seats NUMBER(10) NOT NULL,
    price_per_day NUMBER(12, 2) NOT NULL,
    currency VARCHAR2(3 CHAR) DEFAULT 'BRL' NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_car_rental_offers PRIMARY KEY (id),
    CONSTRAINT fk_car_offers_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_car_offers_seats CHECK (seats >= 1),
    CONSTRAINT ck_car_offers_price CHECK (price_per_day > 0),
    CONSTRAINT ck_car_offers_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$'))
);
CREATE INDEX ix_car_offers_city ON car_rental_offers (city_id);

-- Componente opcional de pacote: exatamente uma referência, coerente com o tipo.
CREATE TABLE travel_package_components (
    id RAW(16) NOT NULL,
    travel_package_id RAW(16) NOT NULL,
    component_type VARCHAR2(20 CHAR) NOT NULL,
    room_offer_id RAW(16),
    car_rental_offer_id RAW(16),
    quantity NUMBER(10) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_tp_components PRIMARY KEY (id),
    CONSTRAINT fk_tpc_package FOREIGN KEY (travel_package_id) REFERENCES travel_packages (id),
    CONSTRAINT fk_tpc_room_offer FOREIGN KEY (room_offer_id) REFERENCES room_offers (id),
    CONSTRAINT fk_tpc_car_offer FOREIGN KEY (car_rental_offer_id) REFERENCES car_rental_offers (id),
    CONSTRAINT ck_tpc_quantity CHECK (quantity BETWEEN 1 AND 60),
    CONSTRAINT ck_tpc_reference CHECK (
        (component_type = 'HOTEL_STAY' AND room_offer_id IS NOT NULL AND car_rental_offer_id IS NULL)
        OR (component_type = 'CAR_RENTAL' AND car_rental_offer_id IS NOT NULL AND room_offer_id IS NULL))
);
CREATE INDEX ix_tpc_package ON travel_package_components (travel_package_id);
CREATE INDEX ix_tpc_room_offer ON travel_package_components (room_offer_id);
CREATE INDEX ix_tpc_car_offer ON travel_package_components (car_rental_offer_id);

-- Seed: hotéis nas cidades de destino (nomes fictícios)
INSERT INTO hotels (id, name, city_id, address, stars, description, image_url, status) VALUES (
    HEXTORAW('EE1E0000000040008000000000000001'), 'Pajucara Mar Resort', HEXTORAW('C1770000000040008000000000000002'),
    'Av. Dr. Antonio Gouveia, Pajucara', 5, 'Resort pe na areia com piscinas e restaurante all inclusive.', NULL, 'ACTIVE');
INSERT INTO hotels (id, name, city_id, address, stars, description, image_url, status) VALUES (
    HEXTORAW('EE1E0000000040008000000000000002'), 'Recoleta Plaza Hotel', HEXTORAW('C1770000000040008000000000000003'),
    'Recoleta, Buenos Aires', 4, 'Hotel classico perto dos principais pontos turisticos.', NULL, 'ACTIVE');
INSERT INTO hotels (id, name, city_id, address, stars, description, image_url, status) VALUES (
    HEXTORAW('EE1E0000000040008000000000000003'), 'Lastarria Boutique', HEXTORAW('C1770000000040008000000000000004'),
    'Barrio Lastarria, Santiago', 4, 'Boutique hotel em bairro historico.', NULL, 'ACTIVE');
INSERT INTO hotels (id, name, city_id, address, stars, description, image_url, status) VALUES (
    HEXTORAW('EE1E0000000040008000000000000004'), 'Lake Buena Vista Suites', HEXTORAW('C1770000000040008000000000000005'),
    'Lake Buena Vista, Orlando', 3, 'Suites familiares a poucos minutos dos parques.', NULL, 'ACTIVE');

-- Seed: ofertas de quarto
INSERT INTO room_offers (id, hotel_id, room_type, board_type, max_guests, price_per_night, currency, status) VALUES (
    HEXTORAW('EE2E0000000040008000000000000001'), HEXTORAW('EE1E0000000040008000000000000001'), 'Standard Duplo', 'ALL_INCLUSIVE', 2, 890.00, 'BRL', 'ACTIVE');
INSERT INTO room_offers (id, hotel_id, room_type, board_type, max_guests, price_per_night, currency, status) VALUES (
    HEXTORAW('EE2E0000000040008000000000000002'), HEXTORAW('EE1E0000000040008000000000000001'), 'Luxo Vista Mar Familia', 'ALL_INCLUSIVE', 4, 1450.00, 'BRL', 'ACTIVE');
INSERT INTO room_offers (id, hotel_id, room_type, board_type, max_guests, price_per_night, currency, status) VALUES (
    HEXTORAW('EE2E0000000040008000000000000003'), HEXTORAW('EE1E0000000040008000000000000002'), 'Superior Duplo', 'BREAKFAST', 2, 520.00, 'BRL', 'ACTIVE');
INSERT INTO room_offers (id, hotel_id, room_type, board_type, max_guests, price_per_night, currency, status) VALUES (
    HEXTORAW('EE2E0000000040008000000000000004'), HEXTORAW('EE1E0000000040008000000000000003'), 'Classico Casal', 'BREAKFAST', 2, 610.00, 'BRL', 'ACTIVE');
INSERT INTO room_offers (id, hotel_id, room_type, board_type, max_guests, price_per_night, currency, status) VALUES (
    HEXTORAW('EE2E0000000040008000000000000005'), HEXTORAW('EE1E0000000040008000000000000004'), 'Suite Familia 2 Quartos', 'ROOM_ONLY', 6, 180.00, 'USD', 'ACTIVE');

-- Seed: aluguel de carro
INSERT INTO car_rental_offers (id, city_id, company_name, car_model, car_category, transmission, seats, price_per_day, currency, status) VALUES (
    HEXTORAW('CA2E0000000040008000000000000001'), HEXTORAW('C1770000000040008000000000000002'), 'Locadora Litoral', 'Fiat Argo ou similar', 'ECONOMY', 'MANUAL', 5, 119.90, 'BRL', 'ACTIVE');
INSERT INTO car_rental_offers (id, city_id, company_name, car_model, car_category, transmission, seats, price_per_day, currency, status) VALUES (
    HEXTORAW('CA2E0000000040008000000000000002'), HEXTORAW('C1770000000040008000000000000005'), 'Sunshine Rentals', 'Minivan 7 lugares ou similar', 'MINIVAN', 'AUTOMATIC', 7, 75.00, 'USD', 'ACTIVE');
INSERT INTO car_rental_offers (id, city_id, company_name, car_model, car_category, transmission, seats, price_per_day, currency, status) VALUES (
    HEXTORAW('CA2E0000000040008000000000000003'), HEXTORAW('C1770000000040008000000000000003'), 'Autos del Plata', 'Hatch compacto ou similar', 'COMPACT', 'MANUAL', 5, 160.00, 'BRL', 'ACTIVE');

-- Seed: componentes dos pacotes existentes
-- Maceio (7 noites): hotel all inclusive + carro por 3 dias
INSERT INTO travel_package_components (id, travel_package_id, component_type, room_offer_id, car_rental_offer_id, quantity) VALUES (
    HEXTORAW('CC0E0000000040008000000000000001'), HEXTORAW('BAC00000000040008000000000000001'), 'HOTEL_STAY', HEXTORAW('EE2E0000000040008000000000000001'), NULL, 7);
INSERT INTO travel_package_components (id, travel_package_id, component_type, room_offer_id, car_rental_offer_id, quantity) VALUES (
    HEXTORAW('CC0E0000000040008000000000000002'), HEXTORAW('BAC00000000040008000000000000001'), 'CAR_RENTAL', NULL, HEXTORAW('CA2E0000000040008000000000000001'), 3);
-- Buenos Aires (7 noites): só hotel
INSERT INTO travel_package_components (id, travel_package_id, component_type, room_offer_id, car_rental_offer_id, quantity) VALUES (
    HEXTORAW('CC0E0000000040008000000000000003'), HEXTORAW('BAC00000000040008000000000000002'), 'HOTEL_STAY', HEXTORAW('EE2E0000000040008000000000000003'), NULL, 7);
