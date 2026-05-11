DROP INDEX IF EXISTS idx_price_history_unique;

CREATE UNIQUE INDEX IF NOT EXISTS idx_price_history_unique_with_source
    ON market_price_history (instrument_id, interval_type, price_timestamp, source_name);

CREATE INDEX IF NOT EXISTS idx_price_history_lookup_with_source_time
    ON market_price_history (instrument_id, interval_type, source_name, price_timestamp);
