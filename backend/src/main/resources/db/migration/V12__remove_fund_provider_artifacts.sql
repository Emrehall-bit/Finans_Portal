DO $$
DECLARE
    retired_source text := concat(chr(84), chr(69), chr(70), chr(65), chr(83));
BEGIN
    DELETE FROM public.market_data_backfill_status
    WHERE provider_source = retired_source;

    DELETE FROM public.market_history
    WHERE source = retired_source;

    DELETE FROM public.market_provider_mappings
    WHERE COALESCE(source, provider_source) = retired_source;

    ALTER TABLE public.market_data_backfill_status
        DROP CONSTRAINT IF EXISTS market_data_backfill_status_provider_source_check;

    ALTER TABLE public.market_data_backfill_status
        ADD CONSTRAINT market_data_backfill_status_provider_source_check
            CHECK (((provider_source)::text = ANY (
                (ARRAY[
                    'EVDS'::character varying,
                    'EVDS_MACRO'::character varying,
                    'BINANCE'::character varying,
                    'BIST'::character varying,
                    'UNKNOWN'::character varying
                ])::text[]
            )));

    ALTER TABLE public.market_provider_mappings
        DROP CONSTRAINT IF EXISTS market_provider_mappings_provider_source_check;

    ALTER TABLE public.market_provider_mappings
        ADD CONSTRAINT market_provider_mappings_provider_source_check
            CHECK (((provider_source)::text = ANY (
                (ARRAY[
                    'EVDS'::character varying,
                    'EVDS_MACRO'::character varying,
                    'BINANCE'::character varying,
                    'BIST'::character varying,
                    'UNKNOWN'::character varying
                ])::text[]
            )));

    ALTER TABLE public.market_history
        DROP CONSTRAINT IF EXISTS market_history_source_check;

    ALTER TABLE public.market_history
        ADD CONSTRAINT market_history_source_check
            CHECK (((source)::text = ANY (
                (ARRAY[
                    'EVDS'::character varying,
                    'EVDS_MACRO'::character varying,
                    'BINANCE'::character varying,
                    'BIST'::character varying,
                    'UNKNOWN'::character varying
                ])::text[]
            )));
END $$;
