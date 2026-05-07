CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE public.market_instruments
    ALTER COLUMN id SET DATA TYPE uuid USING id::uuid,
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE public.market_provider_mappings
    ALTER COLUMN id SET DATA TYPE uuid USING id::uuid,
    ALTER COLUMN id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN instrument_id SET DATA TYPE uuid USING instrument_id::uuid;
