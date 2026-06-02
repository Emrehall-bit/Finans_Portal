-- AKFEN ve AKFIS enstrümanlarını ve ilgili tüm verilerini kaldır.
-- BistSymbolRegistry'den çıkarıldı; yeniden oluşmamaları için DB'den de temizleniyor.

DO $$
DECLARE
    target_ids BIGINT[];
BEGIN
    SELECT ARRAY_AGG(id)
    INTO target_ids
    FROM market_instruments
    WHERE instrument_code IN ('AKFEN', 'AKFIS')
      AND instrument_type = 'STOCK';

    IF target_ids IS NULL OR ARRAY_LENGTH(target_ids, 1) = 0 THEN
        RETURN;
    END IF;

    DELETE FROM market_price_history  WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_prices         WHERE instrument_id = ANY(target_ids);
    DELETE FROM fundamental_history   WHERE instrument_id = ANY(target_ids);
    DELETE FROM fundamental_ratios    WHERE instrument_id = ANY(target_ids);
    DELETE FROM company_financials    WHERE instrument_id = ANY(target_ids);
    DELETE FROM chart_drawings        WHERE instrument_id = ANY(target_ids);
    DELETE FROM indicator_configs     WHERE instrument_id = ANY(target_ids);
    DELETE FROM market_instruments    WHERE id = ANY(target_ids);

END $$;

DELETE FROM watchlist         WHERE instrument_code IN ('AKFEN', 'AKFIS');
DELETE FROM alerts            WHERE instrument_code IN ('AKFEN', 'AKFIS');
DELETE FROM portfolio_holdings WHERE instrument_code IN ('AKFEN', 'AKFIS');
