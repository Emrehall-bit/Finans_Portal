-- Remove global stock indexes seeded in V30
DELETE FROM market_price_history
WHERE instrument_id IN (
    SELECT id FROM market_instruments
    WHERE instrument_code IN ('SP500', 'NASDAQ', 'DOWJONES', 'DAX')
      AND instrument_type = 'INDEX'
      AND source_name = 'YAHOO_FINANCE'
);

DELETE FROM market_prices
WHERE instrument_id IN (
    SELECT id FROM market_instruments
    WHERE instrument_code IN ('SP500', 'NASDAQ', 'DOWJONES', 'DAX')
      AND instrument_type = 'INDEX'
      AND source_name = 'YAHOO_FINANCE'
);

DELETE FROM market_instruments
WHERE instrument_code IN ('SP500', 'NASDAQ', 'DOWJONES', 'DAX')
  AND instrument_type = 'INDEX'
  AND source_name = 'YAHOO_FINANCE';

-- Remove old commodity instruments seeded in V30 (replaced by Turkish-focused list)
DELETE FROM market_price_history
WHERE instrument_id IN (
    SELECT id FROM market_instruments
    WHERE instrument_code IN ('GOLD', 'SILVER', 'WTI', 'NATGAS')
      AND instrument_type = 'COMMODITY'
      AND source_name = 'YAHOO_FINANCE'
);

DELETE FROM market_prices
WHERE instrument_id IN (
    SELECT id FROM market_instruments
    WHERE instrument_code IN ('GOLD', 'SILVER', 'WTI', 'NATGAS')
      AND instrument_type = 'COMMODITY'
      AND source_name = 'YAHOO_FINANCE'
);

DELETE FROM market_instruments
WHERE instrument_code IN ('GOLD', 'SILVER', 'WTI', 'NATGAS')
  AND instrument_type = 'COMMODITY'
  AND source_name = 'YAHOO_FINANCE';

-- Add remaining BIST indexes (BIST100 and BIST30 already seeded in V30)
INSERT INTO market_instruments (instrument_code, instrument_name, instrument_type, source_name, created_at)
VALUES
    ('BIST50', 'BIST 50',        'INDEX', 'YAHOO_FINANCE', NOW()),
    ('XBANK',  'BIST Banka',     'INDEX', 'YAHOO_FINANCE', NOW()),
    ('XUSIN',  'BIST Sınai',     'INDEX', 'YAHOO_FINANCE', NOW()),
    ('XUTEK',  'BIST Teknoloji', 'INDEX', 'YAHOO_FINANCE', NOW()),
    ('XHOLD',  'BIST Holding',   'INDEX', 'YAHOO_FINANCE', NOW())
ON CONFLICT (instrument_code, source_name) DO NOTHING;

-- Add Turkish commodity placeholders (prices populated by scheduler at runtime)
INSERT INTO market_instruments (instrument_code, instrument_name, instrument_type, source_name, created_at)
VALUES
    ('GRAM_ALTIN',        'Gram Altın',        'COMMODITY', 'CALCULATED', NOW()),
    ('CEYREK_ALTIN',      'Çeyrek Altın',      'COMMODITY', 'CALCULATED', NOW()),
    ('YARIM_ALTIN',       'Yarım Altın',        'COMMODITY', 'CALCULATED', NOW()),
    ('TAM_ALTIN',         'Tam Altın',          'COMMODITY', 'CALCULATED', NOW()),
    ('CUMHURIYET_ALTINI', 'Cumhuriyet Altını',  'COMMODITY', 'CALCULATED', NOW()),
    ('GUMUS_GRAM',        'Gümüş Gram',         'COMMODITY', 'CALCULATED', NOW())
ON CONFLICT (instrument_code, source_name) DO NOTHING;
