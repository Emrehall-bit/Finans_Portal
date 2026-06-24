-- BTT ve FIS kripto enstrümanlarını ve bağlı tüm verileri siler.
-- FK zinciri sırasına göre child tablolar önce temizlenir.

DO $$
DECLARE
    btt_fis_ids BIGINT[];
BEGIN
    SELECT ARRAY_AGG(id)
    INTO btt_fis_ids
    FROM market_instruments
    WHERE UPPER(instrument_code) IN ('BTT', 'FIS')
      AND UPPER(instrument_type) = 'CRYPTO';

    -- Enstrüman yoksa işlem yapma
    IF btt_fis_ids IS NULL OR array_length(btt_fis_ids, 1) = 0 THEN
        RETURN;
    END IF;

    -- 1. market_prices (CASCADE yok)
    DELETE FROM market_prices
    WHERE instrument_id = ANY(btt_fis_ids);

    -- 2. market_price_history (CASCADE yok)
    DELETE FROM market_price_history
    WHERE instrument_id = ANY(btt_fis_ids);

    -- 3. V46 analiz tabloları (CASCADE yok)
    DELETE FROM indicator_configs    WHERE instrument_id = ANY(btt_fis_ids);
    DELETE FROM chart_drawings       WHERE instrument_id = ANY(btt_fis_ids);
    DELETE FROM company_financials   WHERE instrument_id = ANY(btt_fis_ids);
    DELETE FROM fundamental_ratios   WHERE instrument_id = ANY(btt_fis_ids);
    DELETE FROM fundamental_history  WHERE instrument_id = ANY(btt_fis_ids);

    -- 4. Ana enstrüman kaydı
    DELETE FROM market_instruments
    WHERE id = ANY(btt_fis_ids);
END;
$$;
