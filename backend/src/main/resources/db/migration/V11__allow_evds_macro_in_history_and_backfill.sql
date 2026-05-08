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
