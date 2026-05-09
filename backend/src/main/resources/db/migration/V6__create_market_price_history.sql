CREATE TABLE market_price_history (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES market_instruments(id),
    open_price NUMERIC(19,8),
    high_price NUMERIC(19,8),
    low_price NUMERIC(19,8),
    close_price NUMERIC(19,8),
    volume NUMERIC(30,8),
    price_timestamp TIMESTAMP NOT NULL,
    interval_type VARCHAR(10) NOT NULL,
    source_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_price_history_instrument_time
    ON market_price_history (instrument_id, price_timestamp DESC);

CREATE UNIQUE INDEX idx_price_history_unique
    ON market_price_history (instrument_id, price_timestamp, interval_type);
