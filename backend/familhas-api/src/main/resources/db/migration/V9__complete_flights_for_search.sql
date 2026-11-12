-- V9: completa o voo para a busca da Fase 5 (ADR 0006).
--
-- O voo continua sendo um registro de ida e volta. Ganha origem e destino (cidades do catálogo),
-- preço por passageiro com moeda ISO 4217, assentos disponíveis e status (só ACTIVE aparece na Loja).
-- As datas do seed passaram (set/out de 2026): o seed vai para 2027 e ganha um voo Brasília -> Maceió.

ALTER TABLE flights ADD (
    origin_city_id      RAW(16),
    destination_city_id RAW(16),
    price_per_passenger NUMBER(12, 2),
    currency            VARCHAR2(3 CHAR)  DEFAULT 'BRL' NOT NULL,
    seats_available     NUMBER(10),
    status              VARCHAR2(20 CHAR) DEFAULT 'ACTIVE' NOT NULL);

-- Gol: São Paulo -> Maceió
UPDATE flights SET
    origin_city_id = HEXTORAW('C1770000000040008000000000000001'),
    destination_city_id = HEXTORAW('C1770000000040008000000000000002'),
    price_per_passenger = 899.90, seats_available = 120,
    boarding_date = TIMESTAMP '2027-03-10 06:20:00', return_date = TIMESTAMP '2027-03-17 20:45:00'
 WHERE id = HEXTORAW('F1170000000040008000000000000001');

-- LATAM: São Paulo -> Buenos Aires
UPDATE flights SET
    origin_city_id = HEXTORAW('C1770000000040008000000000000001'),
    destination_city_id = HEXTORAW('C1770000000040008000000000000003'),
    price_per_passenger = 1290.00, seats_available = 80,
    boarding_date = TIMESTAMP '2027-04-03 11:10:00', return_date = TIMESTAMP '2027-04-10 22:30:00'
 WHERE id = HEXTORAW('F1170000000040008000000000000002');

-- Azul: Brasília -> São Paulo
UPDATE flights SET
    origin_city_id = HEXTORAW('C1770000000040008000000000000006'),
    destination_city_id = HEXTORAW('C1770000000040008000000000000001'),
    price_per_passenger = 459.00, seats_available = 60,
    boarding_date = TIMESTAMP '2027-05-22 06:20:00', return_date = TIMESTAMP '2027-05-25 20:45:00'
 WHERE id = HEXTORAW('F1170000000040008000000000000003');

-- Novo: Azul Brasília -> Maceió, para a busca ter mais de uma origem para o mesmo destino
INSERT INTO flights (id, company_name, boarding_date, return_date, boarding_airport, return_airport,
                     origin_city_id, destination_city_id, price_per_passenger, currency, seats_available, status)
VALUES (HEXTORAW('F1170000000040008000000000000004'), 'AZUL',
        TIMESTAMP '2027-03-10 08:05:00', TIMESTAMP '2027-03-17 18:30:00',
        'BSB - Aeroporto Internacional de Brasilia', 'MCZ - Zumbi dos Palmares',
        HEXTORAW('C1770000000040008000000000000006'), HEXTORAW('C1770000000040008000000000000002'),
        749.00, 'BRL', 4, 'ACTIVE');

ALTER TABLE flights MODIFY (
    origin_city_id      NOT NULL,
    destination_city_id NOT NULL,
    price_per_passenger NOT NULL,
    seats_available     NOT NULL);

ALTER TABLE flights ADD (
    CONSTRAINT fk_flights_origin_city FOREIGN KEY (origin_city_id) REFERENCES cities (id),
    CONSTRAINT fk_flights_destination_city FOREIGN KEY (destination_city_id) REFERENCES cities (id),
    CONSTRAINT ck_flights_price CHECK (price_per_passenger >= 0),
    CONSTRAINT ck_flights_seats CHECK (seats_available >= 0),
    CONSTRAINT ck_flights_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$')),
    CONSTRAINT ck_flights_dates CHECK (return_date > boarding_date),
    CONSTRAINT ck_flights_route CHECK (origin_city_id <> destination_city_id));

-- Busca: rota + data de embarque
CREATE INDEX ix_flights_route_boarding ON flights (origin_city_id, destination_city_id, boarding_date);
CREATE INDEX ix_flights_destination_city ON flights (destination_city_id);
