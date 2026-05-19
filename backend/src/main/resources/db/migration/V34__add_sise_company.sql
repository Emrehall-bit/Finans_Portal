INSERT INTO company_profiles (ticker_code, company_name, sector, market, kap_company_id, active, created_at, updated_at)
VALUES ('SISE', 'Türkiye Şişe ve Cam Fabrikaları A.Ş.', 'Cam ve Ambalaj', 'BIST', '1087', TRUE, NOW(), NOW())
ON CONFLICT (ticker_code) DO UPDATE
    SET kap_company_id = EXCLUDED.kap_company_id,
        updated_at     = NOW();
