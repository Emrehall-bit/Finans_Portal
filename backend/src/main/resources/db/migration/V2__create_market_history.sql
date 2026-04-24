CREATE TABLE market_history (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(50) NOT NULL,
    display_name VARCHAR(255),
    instrument_type VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    price_date DATE NOT NULL,
    close_price NUMERIC(19, 6) NOT NULL,
    currency VARCHAR(20),
    created_at TIMESTAMP NOT NULL
);

ALTER TABLE market_history
    ADD CONSTRAINT uk_market_history_symbol_source_date UNIQUE (symbol, source, price_date);

CREATE INDEX idx_market_history_symbol ON market_history (symbol);
CREATE INDEX idx_market_history_source ON market_history (source);
CREATE INDEX idx_market_history_price_date ON market_history (price_date);
CREATE INDEX idx_market_history_symbol_price_date ON market_history (symbol, price_date);
