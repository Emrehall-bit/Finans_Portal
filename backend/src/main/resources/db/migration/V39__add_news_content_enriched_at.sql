ALTER TABLE public.news
    ADD COLUMN IF NOT EXISTS content_enriched_at timestamp without time zone;
