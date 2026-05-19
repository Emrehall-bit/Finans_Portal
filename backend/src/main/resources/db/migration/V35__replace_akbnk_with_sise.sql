DELETE FROM company_profiles WHERE ticker_code = 'AKBNK';

INSERT INTO company_profiles (ticker_code, company_name, sector, market, kap_company_id, shares_outstanding, active, created_at, updated_at)
VALUES ('SISE', 'Türkiye Şişe ve Cam Fabrikaları A.Ş.', 'Cam ve Ambalaj', 'BIST', '1087', 3947236780, TRUE, NOW(), NOW())
ON CONFLICT (ticker_code) DO UPDATE
    SET kap_company_id     = EXCLUDED.kap_company_id,
        shares_outstanding = EXCLUDED.shares_outstanding,
        updated_at         = NOW();
