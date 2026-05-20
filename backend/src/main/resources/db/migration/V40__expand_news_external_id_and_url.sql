ALTER TABLE public.news
    ALTER COLUMN external_id TYPE TEXT;

ALTER TABLE public.news
    ALTER COLUMN url TYPE TEXT;

ALTER TABLE public.news
    ALTER COLUMN image_url TYPE TEXT;

ALTER TABLE public.news
    ALTER COLUMN summary TYPE TEXT;

ALTER TABLE public.news
    ALTER COLUMN title TYPE character varying(500);
