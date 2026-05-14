-- TRY (Türk Lirası) is a fixed-price internal instrument. It is never updated by any scheduler
-- or external provider. Price is always 1.00 by definition.

INSERT INTO market_instruments (instrument_code, instrument_name, instrument_type, source_name, created_at)
VALUES ('TRY', 'Türk Lirası', 'FX', 'INTERNAL', NOW())
ON CONFLICT (instrument_code, source_name) DO NOTHING;

INSERT INTO market_prices (instrument_id, price_value, change_rate, price_timestamp, source_name)
SELECT i.id, 1.000000, 0.0000, NOW(), 'INTERNAL'
FROM market_instruments i
WHERE i.instrument_code = 'TRY'
  AND NOT EXISTS (
      SELECT 1 FROM market_prices p WHERE p.instrument_id = i.id
  );
