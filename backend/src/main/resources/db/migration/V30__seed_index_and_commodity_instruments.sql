-- Seed global stock index instruments
INSERT INTO market_instruments (instrument_code, instrument_name, instrument_type, source_name, created_at)
VALUES
    ('BIST100',  'BIST 100',           'INDEX', 'YAHOO_FINANCE', NOW()),
    ('BIST30',   'BIST 30',            'INDEX', 'YAHOO_FINANCE', NOW()),
    ('SP500',    'S&P 500',            'INDEX', 'YAHOO_FINANCE', NOW()),
    ('NASDAQ',   'Nasdaq Composite',   'INDEX', 'YAHOO_FINANCE', NOW()),
    ('DOWJONES', 'Dow Jones',          'INDEX', 'YAHOO_FINANCE', NOW()),
    ('DAX',      'DAX',                'INDEX', 'YAHOO_FINANCE', NOW())
ON CONFLICT (instrument_code, source_name) DO NOTHING;

-- Seed commodity instruments
INSERT INTO market_instruments (instrument_code, instrument_name, instrument_type, source_name, created_at)
VALUES
    ('GOLD',   'Altın (Vadeli)',  'COMMODITY', 'YAHOO_FINANCE', NOW()),
    ('SILVER', 'Gümüş (Vadeli)', 'COMMODITY', 'YAHOO_FINANCE', NOW()),
    ('BRENT',  'Brent Petrol',   'COMMODITY', 'YAHOO_FINANCE', NOW()),
    ('WTI',    'Ham Petrol',     'COMMODITY', 'YAHOO_FINANCE', NOW()),
    ('NATGAS', 'Doğalgaz',       'COMMODITY', 'YAHOO_FINANCE', NOW())
ON CONFLICT (instrument_code, source_name) DO NOTHING;
