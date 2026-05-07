CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE public.market_instruments
    ADD COLUMN IF NOT EXISTS id_uuid uuid DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS display_name character varying(255),
    ADD COLUMN IF NOT EXISTS type character varying(50),
    ADD COLUMN IF NOT EXISTS enabled boolean;

UPDATE public.market_instruments
SET id_uuid = COALESCE(id_uuid, gen_random_uuid()),
    display_name = COALESCE(display_name, name, symbol),
    type = COALESCE(
            type,
            CASE instrument_type
                WHEN 'CURRENCY' THEN 'FOREX'
                WHEN 'FX' THEN 'FOREX'
                WHEN 'GOLD' THEN 'COMMODITY'
                WHEN 'COMMODITY' THEN 'COMMODITY'
                WHEN 'STOCK' THEN 'STOCK'
                WHEN 'FUND' THEN 'FUND'
                WHEN 'CRYPTO' THEN 'CRYPTO'
                WHEN 'INDEX' THEN 'INDEX'
                WHEN 'VIOP' THEN 'BOND'
                WHEN 'IPO' THEN 'INDEX'
                ELSE 'INDEX'
                END
           ),
    enabled = COALESCE(enabled, active, true);

ALTER TABLE public.market_instruments
    ALTER COLUMN id_uuid SET NOT NULL,
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN enabled SET NOT NULL;

ALTER TABLE public.market_provider_mappings
    ADD COLUMN IF NOT EXISTS id_uuid uuid DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS instrument_id_uuid uuid,
    ADD COLUMN IF NOT EXISTS source character varying(50),
    ADD COLUMN IF NOT EXISTS external_symbol character varying(100),
    ADD COLUMN IF NOT EXISTS refresh_interval_minutes integer,
    ADD COLUMN IF NOT EXISTS history_start_date date,
    ADD COLUMN IF NOT EXISTS last_refreshed_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS last_refresh_status character varying(20);

UPDATE public.market_provider_mappings mapping
SET id_uuid = COALESCE(mapping.id_uuid, gen_random_uuid()),
    instrument_id_uuid = COALESCE(
            mapping.instrument_id_uuid,
            instrument.id_uuid
        ),
    source = COALESCE(mapping.source, mapping.provider_source),
    external_symbol = COALESCE(mapping.external_symbol, mapping.provider_symbol),
    refresh_interval_minutes = COALESCE(
            mapping.refresh_interval_minutes,
            GREATEST(COALESCE(mapping.refresh_interval_seconds / 60, 0), 1)
        ),
    history_start_date = COALESCE(
            mapping.history_start_date,
            CASE mapping.provider_source
                WHEN 'BINANCE' THEN DATE '2017-01-01'
                ELSE DATE '2000-01-01'
                END
        ),
    last_refresh_status = COALESCE(mapping.last_refresh_status, 'PENDING')
FROM public.market_instruments instrument
WHERE mapping.instrument_id = instrument.id;

ALTER TABLE public.market_provider_mappings
    ALTER COLUMN id_uuid SET NOT NULL,
    ALTER COLUMN instrument_id_uuid SET NOT NULL,
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN external_symbol SET NOT NULL,
    ALTER COLUMN refresh_interval_minutes SET NOT NULL,
    ALTER COLUMN last_refresh_status SET NOT NULL;

ALTER TABLE public.market_provider_mappings DROP CONSTRAINT IF EXISTS fk_market_provider_mappings_instrument;
ALTER TABLE public.market_provider_mappings DROP CONSTRAINT IF EXISTS market_provider_mappings_pkey;
ALTER TABLE public.market_instruments DROP CONSTRAINT IF EXISTS market_instruments_pkey;
ALTER TABLE public.market_instruments DROP CONSTRAINT IF EXISTS uk_market_instruments_symbol;
ALTER TABLE public.market_provider_mappings DROP CONSTRAINT IF EXISTS uk_market_provider_mappings_source_symbol;

ALTER TABLE public.market_instruments RENAME COLUMN id TO id_legacy;
ALTER TABLE public.market_instruments RENAME COLUMN id_uuid TO id;

ALTER TABLE public.market_provider_mappings RENAME COLUMN id TO id_legacy;
ALTER TABLE public.market_provider_mappings RENAME COLUMN id_uuid TO id;
ALTER TABLE public.market_provider_mappings RENAME COLUMN instrument_id TO instrument_id_legacy;
ALTER TABLE public.market_provider_mappings RENAME COLUMN instrument_id_uuid TO instrument_id;

ALTER TABLE public.market_instruments
    ADD CONSTRAINT market_instruments_pkey PRIMARY KEY (id);

ALTER TABLE public.market_provider_mappings
    ADD CONSTRAINT market_provider_mappings_pkey PRIMARY KEY (id);

ALTER TABLE public.market_instruments
    ADD CONSTRAINT uk_market_instruments_symbol UNIQUE (symbol);

ALTER TABLE public.market_provider_mappings
    ADD CONSTRAINT uk_market_provider_mappings_source_symbol UNIQUE (source, external_symbol);

ALTER TABLE public.market_provider_mappings
    ADD CONSTRAINT fk_market_provider_mappings_instrument
        FOREIGN KEY (instrument_id) REFERENCES public.market_instruments(id);

DROP INDEX IF EXISTS public.idx_market_instruments_active;
DROP INDEX IF EXISTS public.idx_market_provider_mappings_instrument_id;
DROP INDEX IF EXISTS public.idx_market_provider_mappings_source_enabled_priority;

CREATE INDEX IF NOT EXISTS idx_market_instruments_symbol ON public.market_instruments USING btree (symbol);
CREATE INDEX IF NOT EXISTS idx_market_instruments_type_enabled ON public.market_instruments USING btree (type, enabled);
CREATE INDEX IF NOT EXISTS idx_market_provider_mappings_source_enabled ON public.market_provider_mappings USING btree (source, enabled);
CREATE INDEX IF NOT EXISTS idx_market_provider_mappings_instrument_priority ON public.market_provider_mappings USING btree (instrument_id, priority);
