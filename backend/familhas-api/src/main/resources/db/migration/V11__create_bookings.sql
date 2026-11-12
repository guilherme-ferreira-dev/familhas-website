-- V11: reserva (guia, Fase 6; ADR 0007).
--
-- A reserva nasce de uma cotação OPEN e copia o snapshot de preços dela: o total cobrado é o da cotação.
-- Uma cotação vira no máximo uma reserva (uq_bookings_quote) e passa a BOOKED.
-- Os assentos do voo do catálogo curado ficam bloqueados enquanto a reserva não é paga nem cancelada:
-- held_flight_id + held_seats guardam o que devolver em flights.seats_available no cancelamento.

ALTER TABLE quotes DROP CONSTRAINT ck_quotes_status;
ALTER TABLE quotes ADD CONSTRAINT ck_quotes_status CHECK (status IN ('OPEN', 'EXPIRED', 'BOOKED'));

CREATE TABLE bookings (
    id                  RAW(16)                      NOT NULL,
    client_id           RAW(16)                      NOT NULL,
    quote_id            RAW(16)                      NOT NULL,
    travel_package_id   RAW(16),
    status              VARCHAR2(20 CHAR)            NOT NULL,
    passengers          NUMBER(10)                   NOT NULL,
    currency            VARCHAR2(3 CHAR)             NOT NULL,
    components_total    NUMBER(12, 2)                NOT NULL,
    margin_percent      NUMBER(7, 4)                 NOT NULL,
    margin_amount       NUMBER(12, 2)                NOT NULL,
    promotion_percent   NUMBER(7, 4)                 NOT NULL,
    discount_amount     NUMBER(12, 2)                NOT NULL,
    service_fee_amount  NUMBER(12, 2)                NOT NULL,
    total_amount        NUMBER(12, 2)                NOT NULL,
    expires_at          TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    confirmed_at        TIMESTAMP(6) WITH TIME ZONE,
    cancelled_at        TIMESTAMP(6) WITH TIME ZONE,
    cancellation_reason VARCHAR2(30 CHAR),
    held_flight_id      RAW(16),
    held_seats          NUMBER(10)   DEFAULT 0       NOT NULL,
    created_at          TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT uq_bookings_quote UNIQUE (quote_id),
    CONSTRAINT fk_bookings_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_bookings_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_bookings_package FOREIGN KEY (travel_package_id) REFERENCES travel_packages (id),
    CONSTRAINT fk_bookings_held_flight FOREIGN KEY (held_flight_id) REFERENCES flights (id),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_bookings_cancellation CHECK (cancellation_reason IS NULL
        OR cancellation_reason IN ('PAYMENT_EXPIRED', 'REFUNDED')),
    CONSTRAINT ck_bookings_passengers CHECK (passengers BETWEEN 1 AND 9),
    CONSTRAINT ck_bookings_currency CHECK (REGEXP_LIKE(currency, '^[A-Z]{3}$')),
    CONSTRAINT ck_bookings_total CHECK (total_amount >= 0),
    CONSTRAINT ck_bookings_held_seats CHECK (held_seats >= 0)
);

-- "Minhas reservas": por cliente, mais recentes primeiro.
CREATE INDEX ix_bookings_client_created ON bookings (client_id, created_at);
-- Reconciliação: reservas PENDING_PAYMENT vencidas.
CREATE INDEX ix_bookings_status_expires ON bookings (status, expires_at);
CREATE INDEX ix_bookings_package ON bookings (travel_package_id);
CREATE INDEX ix_bookings_held_flight ON bookings (held_flight_id);

CREATE TABLE booking_items (
    id           RAW(16)                      NOT NULL,
    booking_id   RAW(16)                      NOT NULL,
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
    CONSTRAINT pk_booking_items PRIMARY KEY (id),
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_items_type CHECK (item_type IN ('FLIGHT', 'HOTEL_STAY', 'CAR_RENTAL')),
    CONSTRAINT ck_booking_items_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_booking_items_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_booking_items_booking ON booking_items (booking_id);
