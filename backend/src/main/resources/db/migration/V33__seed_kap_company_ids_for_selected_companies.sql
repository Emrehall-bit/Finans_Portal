UPDATE company_profiles
SET kap_company_id = '1107',
    updated_at = NOW()
WHERE ticker_code = 'THYAO'
  AND (kap_company_id IS NULL OR kap_company_id <> '1107');

UPDATE company_profiles
SET kap_company_id = '1105',
    updated_at = NOW()
WHERE ticker_code = 'TUPRS'
  AND (kap_company_id IS NULL OR kap_company_id <> '1105');

UPDATE company_profiles
SET kap_company_id = '1005',
    updated_at = NOW()
WHERE ticker_code = 'KCHOL'
  AND (kap_company_id IS NULL OR kap_company_id <> '1005');

UPDATE company_profiles
SET kap_company_id = '1406',
    updated_at = NOW()
WHERE ticker_code = 'BIMAS'
  AND (kap_company_id IS NULL OR kap_company_id <> '1406');
