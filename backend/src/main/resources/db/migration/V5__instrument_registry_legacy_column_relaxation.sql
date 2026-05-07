ALTER TABLE public.market_instruments
    ALTER COLUMN active DROP NOT NULL,
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN instrument_type DROP NOT NULL;

ALTER TABLE public.market_provider_mappings
    ALTER COLUMN provider_source DROP NOT NULL,
    ALTER COLUMN provider_symbol DROP NOT NULL,
    ALTER COLUMN instrument_id_legacy DROP NOT NULL,
    ALTER COLUMN priority DROP NOT NULL;
