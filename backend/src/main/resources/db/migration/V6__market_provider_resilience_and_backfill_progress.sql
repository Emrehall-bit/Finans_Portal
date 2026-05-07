ALTER TABLE public.market_provider_mappings
    ADD COLUMN IF NOT EXISTS last_failed_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS last_failure_reason character varying(2000);

ALTER TABLE public.market_data_backfill_status
    ADD COLUMN IF NOT EXISTS total_chunks integer,
    ADD COLUMN IF NOT EXISTS completed_chunks integer,
    ADD COLUMN IF NOT EXISTS last_processed_date date;
