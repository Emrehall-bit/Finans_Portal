ALTER TABLE market_instruments
    ADD COLUMN stock_sector VARCHAR(30)
        CHECK (stock_sector IN (
            'BANKING', 'FINANCIALS', 'TECHNOLOGY_DEFENSE', 'TELECOM',
            'TRANSPORT_TOURISM', 'HEALTH_SPORTS', 'ENERGY',
            'CHEMICALS_PETROLEUM', 'INDUSTRIAL', 'FOOD_RETAIL', 'OTHER'
        ));

CREATE INDEX idx_market_instruments_stock_sector
    ON market_instruments(stock_sector)
    WHERE stock_sector IS NOT NULL;

-- BANKING
UPDATE market_instruments SET stock_sector = 'BANKING'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKBNK','ALBRK','GARAN','HALKB','ISCTR','SKBNK','VAKBN','YKBNK'
);

-- FINANCIALS
UPDATE market_instruments SET stock_sector = 'FINANCIALS'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKGRT','ALARK','BRYAT','EKGYO','GLYHO','HLGYO','ISGYO','ISMEN',
    'KCHOL','KLGYO','KTLEV','PEHOL','RAYSG','SAHOL','SRVGY','TRGYO',
    'TSKB','TURSG','VERUS'
);

-- TECHNOLOGY_DEFENSE
UPDATE market_instruments SET stock_sector = 'TECHNOLOGY_DEFENSE'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'ARDYZ','ASELS','KRONT','LOGO','MIATK','NETAS'
);

-- TELECOM
UPDATE market_instruments SET stock_sector = 'TELECOM'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'TCELL','TTKOM'
);

-- TRANSPORT_TOURISM
UPDATE market_instruments SET stock_sector = 'TRANSPORT_TOURISM'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'CLEBI','FLAP','NTTUR','PGSUS','TAVHL','THYAO'
);

-- HEALTH_SPORTS
UPDATE market_instruments SET stock_sector = 'HEALTH_SPORTS'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'FENER','MPARK','ONCSM'
);

-- ENERGY
UPDATE market_instruments SET stock_sector = 'ENERGY'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKSEN','ALFAS','ASTOR','AYDEM','CANTE','CWENE','ENERY','ENJSA',
    'EUPWR','GESAN','GWIND','IZENR','ODAS','ORGE','SAYAS','ZOREN'
);

-- CHEMICALS_PETROLEUM
UPDATE market_instruments SET stock_sector = 'CHEMICALS_PETROLEUM'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKSA','ALKIM','DYOBY','ECILC','GENIL','GUBRF','HEKTS','PETKM',
    'SASA','TUPRS'
);

-- INDUSTRIAL
UPDATE market_instruments SET stock_sector = 'INDUSTRIAL'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'ANACM','ARCLK','BOBET','BTCIM','BUCIM','CEMTS','CIMSA','DOAS',
    'EGEEN','ENKAI','ERBOS','EREGL','FROTO','GOODY','JANTS','KARSN',
    'KARTN','KONYA','KOZAA','KOZAL','KRDMD','OTKAR','OYAKC','QUAGR',
    'SISE','TOASO','TTRAK','VESTL'
);

-- FOOD_RETAIL
UPDATE market_instruments SET stock_sector = 'FOOD_RETAIL'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'BIMAS','CCOLA','KAYSE','MAVI','MGROS','SOKM','ULKER'
);

-- OTHER: takip edilen ama sektör ataması yapılmamış tüm hisseler
UPDATE market_instruments SET stock_sector = 'OTHER'
WHERE instrument_type = 'STOCK' AND stock_sector IS NULL;
