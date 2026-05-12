ALTER TABLE ONLY public.users
    DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY[
        'GUEST'::character varying,
        'USER'::character varying,
        'ADMIN'::character varying,
        'USER_PREMIUM'::character varying,
        'SYSTEM_ENGINEER'::character varying
    ])::text[])));
