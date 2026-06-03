ALTER TABLE market_instruments
    ADD COLUMN bist_tier VARCHAR(10)
        CHECK (bist_tier IN ('BIST30', 'BIST50', 'BIST100', 'OTHER'));

CREATE INDEX idx_market_instruments_bist_tier
    ON market_instruments(bist_tier)
    WHERE bist_tier IS NOT NULL;

-- BIST30
UPDATE market_instruments
SET bist_tier = 'BIST30'
WHERE instrument_type = 'STOCK'
  AND instrument_code IN (
    'AKBNK', 'ASELS', 'BIMAS', 'EREGL', 'FROTO',
    'GARAN', 'GUBRF', 'ISCTR', 'KCHOL', 'KOZAL',
    'MGROS', 'PETKM', 'PGSUS', 'SAHOL', 'SISE',
    'TCELL', 'THYAO', 'TOASO', 'TUPRS', 'VAKBN',
    'YKBNK'
  );

-- BIST50 (BIST30'da olmayıp BIST50'de olanlar)
UPDATE market_instruments
SET bist_tier = 'BIST50'
WHERE instrument_type = 'STOCK'
  AND instrument_code IN (
    'ALARK', 'ARCLK', 'CCOLA', 'DOAS',  'EKGYO',
    'ENJSA', 'ENKAI', 'HALKB', 'ISMEN', 'KOZAA',
    'KRDMD', 'MAVI',  'MPARK', 'OYAKC', 'SOKM',
    'TAVHL', 'TTKOM', 'TTRAK', 'TSKB',  'TURSG',
    'ULKER'
  );

-- BIST100 (BIST50'de olmayıp BIST100'de olanlar)
UPDATE market_instruments
SET bist_tier = 'BIST100'
WHERE instrument_type = 'STOCK'
  AND instrument_code IN (
    'AKGRT', 'AKSA',  'AKSEN', 'ALBRK', 'ALFAS',
    'ALKIM', 'ARDYZ', 'ASTOR', 'AYDEM', 'BERA',
    'BOBET', 'BRYAT', 'BTCIM', 'BUCIM', 'CANTE',
    'CEMTS', 'CIMSA', 'CLEBI', 'CWENE', 'DOHOL',
    'ECILC', 'EGEEN', 'ENERY', 'ERBOS', 'EUPWR',
    'FAVOR', 'FENER', 'GENIL', 'GESAN', 'GLYHO',
    'GOODY', 'GWIND', 'HEKTS', 'HLGYO', 'ISGYO',
    'IZENR', 'JANTS', 'KARSN', 'KAYSE', 'KLGYO',
    'KONYA', 'KTLEV', 'LOGO',  'MIATK', 'NETAS',
    'ODAS',  'ONCSM', 'ORGE',  'OTKAR', 'QUAGR',
    'RAYSG', 'SAYAS', 'SKBNK', 'SRVGY', 'TKFEN',
    'VERUS', 'VESTL', 'ZOREN'
  );

-- OTHER: takip edilen ama hiçbir endekste olmayan BIST hisseleri
UPDATE market_instruments
SET bist_tier = 'OTHER'
WHERE instrument_type = 'STOCK'
  AND bist_tier IS NULL;
