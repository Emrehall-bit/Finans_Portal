-- kap_company_id repurposed to store the mkkMemberId UUID used in KAP compareItems API payloads
UPDATE company_profiles
SET kap_company_id = '4028e4a140f2ed720140f376bebb01a7',
    updated_at     = NOW()
WHERE ticker_code = 'THYAO';

UPDATE company_profiles
SET kap_company_id = '4028e4a140f2ed710140f385d5690102',
    updated_at     = NOW()
WHERE ticker_code = 'SISE';
