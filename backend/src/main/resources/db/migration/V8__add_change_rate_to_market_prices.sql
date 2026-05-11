ALTER TABLE market_prices
  ADD COLUMN IF NOT EXISTS change_rate NUMERIC(10, 4);
