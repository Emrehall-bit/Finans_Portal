ALTER TABLE company_profiles
    ADD COLUMN IF NOT EXISTS shares_outstanding NUMERIC(30, 4);

UPDATE company_profiles
SET shares_outstanding = 1380000000
WHERE UPPER(ticker_code) = 'THYAO'
  AND shares_outstanding IS NULL;
