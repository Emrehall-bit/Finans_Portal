-- Assign sector mappings for BIST-tier OTHER stocks only.
-- Does not touch BIST tiers, prices, or price history.

-- FINANCIALS
UPDATE market_instruments SET stock_sector = 'FINANCIALS'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AGHOL','AKFGY','AKFYE','AKMGY','ANHYT','AVPGY','BEGYO','INVEO',
    'ISFIN','ISYAT','NTHOL','TRGYO'
);

-- TECHNOLOGY_DEFENSE
UPDATE market_instruments SET stock_sector = 'TECHNOLOGY_DEFENSE'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'ALCTL','DGATE','FORTE','INDES','INFO','KAREL','KRONT','PENTA'
);

-- TRANSPORT_TOURISM
UPDATE market_instruments SET stock_sector = 'TRANSPORT_TOURISM'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'ARASE','FLAP','GRSEL','NTTUR'
);

-- ENERGY
UPDATE market_instruments SET stock_sector = 'ENERGY'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKSUE','BIOEN','CATES','ENTRA'
);

-- CHEMICALS_PETROLEUM
UPDATE market_instruments SET stock_sector = 'CHEMICALS_PETROLEUM'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'AKCNS','ANACM','BSOKE','DYOBY','KARTN','KORDS'
);

-- INDUSTRIAL
UPDATE market_instruments SET stock_sector = 'INDUSTRIAL'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'ASUZU','BASGZ','BFREN','BRISA','DESA','DESPC','EGEEN','GMTAS',
    'ISDMR','KLSER','KMPUR','KNFRT'
);

-- FOOD_RETAIL
UPDATE market_instruments SET stock_sector = 'FOOD_RETAIL'
WHERE instrument_type = 'STOCK' AND instrument_code IN (
    'BIGCH','DEVA','KOTON','KRVGD','SNPAM','YYLGD'
);
