-- V15: margem e taxa de serviço editáveis no Admin (guia, Fase 10; ADR 0011).
--
-- Uma linha só (id = 1). Enquanto ninguém salvar pelo Admin, a tabela fica vazia e valem as variáveis de
-- ambiente (PRICING_MARGIN_PERCENT, PRICING_SERVICE_FEE_PER_PASSENGER, PRICING_SERVICE_FEE_CURRENCY).
-- Cotações e reservas guardam o próprio snapshot: mudar aqui só afeta cálculos novos.

CREATE TABLE pricing_settings (
    id                        NUMBER(1)                    NOT NULL,
    margin_percent            NUMBER(7, 4)                 NOT NULL,
    service_fee_per_passenger NUMBER(12, 2)                NOT NULL,
    service_fee_currency      VARCHAR2(3 CHAR)             NOT NULL,
    updated_by                VARCHAR2(180 CHAR),
    created_at                TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at                TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_pricing_settings PRIMARY KEY (id),
    CONSTRAINT ck_pricing_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_pricing_settings_margin CHECK (margin_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_pricing_settings_fee CHECK (service_fee_per_passenger >= 0),
    CONSTRAINT ck_pricing_settings_currency CHECK (REGEXP_LIKE(service_fee_currency, '^[A-Z]{3}$'))
);
