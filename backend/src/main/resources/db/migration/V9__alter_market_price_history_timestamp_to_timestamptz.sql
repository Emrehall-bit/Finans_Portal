ALTER TABLE market_price_history
  ALTER COLUMN price_timestamp TYPE TIMESTAMPTZ
  USING price_timestamp AT TIME ZONE 'UTC';
