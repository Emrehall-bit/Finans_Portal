-- Fix exclusive BIST tier distribution without touching price/history tables.

UPDATE market_instruments
SET bist_tier = 'BIST50'
WHERE instrument_type = 'STOCK'
  AND instrument_code IN ('BTCIM', 'CANTE', 'CIMSA', 'HEKTS', 'MIATK');

UPDATE market_instruments
SET bist_tier = 'OTHER'
WHERE instrument_type = 'STOCK'
  AND instrument_code = 'EGEEN';
