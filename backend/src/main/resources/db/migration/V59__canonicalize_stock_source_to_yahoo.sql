-- Hisse enstrumanlarinda eski BIST/IS YATIRIM veri yolunu terk edip Yahoo Finance'i canonical kaynak yap.
-- NTTUR ve ANACM aktif BIST evreninde olmadigi icin market verilerinden temizlenir.

UPDATE market_instruments
SET source_name = 'YAHOO_FINANCE'
WHERE instrument_type = 'STOCK'
  AND source_name <> 'YAHOO_FINANCE'
  AND instrument_code NOT IN ('NTTUR', 'ANACM');

DELETE FROM market_price_history mph
USING market_instruments mi
WHERE mph.instrument_id = mi.id
  AND mi.instrument_type = 'STOCK'
  AND mph.source_name <> 'YAHOO_FINANCE';

DELETE FROM market_prices mp
USING market_instruments mi
WHERE mp.instrument_id = mi.id
  AND mi.instrument_type = 'STOCK'
  AND mp.source_name <> 'YAHOO_FINANCE';

DO $$
DECLARE
    target_ids BIGINT[];
BEGIN
    SELECT ARRAY_AGG(id)
    INTO target_ids
    FROM market_instruments
    WHERE instrument_type = 'STOCK'
      AND instrument_code IN ('NTTUR', 'ANACM');

    IF target_ids IS NULL OR ARRAY_LENGTH(target_ids, 1) = 0 THEN
        RETURN;
    END IF;

    DELETE FROM market_price_history WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_prices        WHERE instrument_id = ANY(target_ids);
    DELETE FROM fundamental_history  WHERE instrument_id = ANY(target_ids);
    DELETE FROM fundamental_ratios   WHERE instrument_id = ANY(target_ids);
    DELETE FROM company_financials   WHERE instrument_id = ANY(target_ids);
    DELETE FROM chart_drawings       WHERE instrument_id = ANY(target_ids);
    DELETE FROM indicator_configs    WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_instruments   WHERE id = ANY(target_ids);
END $$;

DELETE FROM watchlist          WHERE instrument_code IN ('NTTUR', 'ANACM');
DELETE FROM alerts             WHERE instrument_code IN ('NTTUR', 'ANACM');
DELETE FROM portfolio_holdings WHERE instrument_code IN ('NTTUR', 'ANACM');
