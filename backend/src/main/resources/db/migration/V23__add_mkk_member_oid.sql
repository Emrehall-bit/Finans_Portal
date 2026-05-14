ALTER TABLE company_profiles
    ADD COLUMN mkk_member_oid VARCHAR(100);

CREATE INDEX idx_company_profiles_mkk_member_oid ON company_profiles (mkk_member_oid);

UPDATE company_profiles
SET mkk_member_oid = '4028e4a140f2ed720140f376bebb01a7'
WHERE ticker_code = 'THYAO';
