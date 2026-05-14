ALTER TABLE company_disclosures
    ADD COLUMN IF NOT EXISTS kap_year   INT,
    ADD COLUMN IF NOT EXISTS kap_donem  INT,
    ADD COLUMN IF NOT EXISTS kap_period VARCHAR(20);
