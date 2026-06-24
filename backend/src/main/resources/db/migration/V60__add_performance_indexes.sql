-- Composite index for batch stock/fund history queries:
-- findLatestDailyHistoriesForInstruments, findPreviousClosesForStockInstruments,
-- findPreviousClosesForInstruments all filter by (instrument_id, interval_type, source_name)
-- and ORDER BY price_timestamp DESC. Without this index each DISTINCT ON scan is sequential.
CREATE INDEX IF NOT EXISTS idx_mph_inst_interval_source_ts
    ON market_price_history (instrument_id, interval_type, source_name, price_timestamp DESC);

-- Supports lookups by instrument_type alone (e.g. count queries, admin views).
CREATE INDEX IF NOT EXISTS idx_mi_instrument_type
    ON market_instruments (instrument_type);

-- Supports the common filter (type + source) used in fund and FX retrieval.
CREATE INDEX IF NOT EXISTS idx_mi_type_source
    ON market_instruments (instrument_type, source_name);
