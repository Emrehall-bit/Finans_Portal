ALTER TABLE public.news
    ADD COLUMN IF NOT EXISTS quality_status character varying(40);

ALTER TABLE public.news
    ADD COLUMN IF NOT EXISTS is_kap_disclosure boolean NOT NULL DEFAULT false;

ALTER TABLE public.news
    ADD COLUMN IF NOT EXISTS disclosure_type character varying(50);
