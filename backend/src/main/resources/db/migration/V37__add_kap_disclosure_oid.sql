ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS kap_disclosure_oid VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_company_profiles_kap_disclosure_oid
    ON company_profiles (kap_disclosure_oid);

-- THYAO: same UUID used for both compareItems and sgbf-data disclosure API
UPDATE company_profiles
SET kap_disclosure_oid = '4028e4a140f2ed720140f376bebb01a7',
    updated_at         = NOW()
WHERE ticker_code = 'THYAO';

-- SISE: same UUID used for both compareItems and sgbf-data disclosure API
UPDATE company_profiles
SET kap_disclosure_oid = '4028e4a140f2ed710140f385d5690102',
    updated_at         = NOW()
WHERE ticker_code = 'SISE';
